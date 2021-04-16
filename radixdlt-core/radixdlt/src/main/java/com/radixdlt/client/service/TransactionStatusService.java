/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.client.api.TransactionStatus;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.AtomsCommittedToLedger;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static com.radixdlt.client.api.TransactionStatus.CONFIRMED;
import static com.radixdlt.client.api.TransactionStatus.FAILED;
import static com.radixdlt.client.api.TransactionStatus.PENDING;
import static com.radixdlt.client.api.TransactionStatus.TRANSACTION_NOT_FOUND;

public class TransactionStatusService {
	private static final long DEFAULT_CLEANUP_INTERVAL = 1000L;                        //every second
	private static final Duration DEFAULT_TX_LIFE_TIME = Duration.ofMinutes(10);    //at most 10 minutes

	private final CompositeDisposable disposable = new CompositeDisposable();
	private final ConcurrentMap<AID, TxStatusEntry> txCache = new ConcurrentHashMap<>();
	private final BerkeleyLedgerEntryStore store;
	private final ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup;

	@Inject
	public TransactionStatusService(
		BerkeleyLedgerEntryStore store,
		Observable<AtomsCommittedToLedger> committed,
		Observable<MempoolAddFailure> rejected,
		Observable<MempoolAddSuccess> succeeded,
		ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup
	) {
		this.store = store;
		this.scheduledCacheCleanup = scheduledCacheCleanup;

		disposable.add(committed.subscribe(this::onCommit));
		disposable.add(rejected.subscribe(this::onReject));
		disposable.add(succeeded.subscribe(this::onSuccess));
		scheduledCacheCleanup.dispatch(ScheduledCacheCleanup.create(), DEFAULT_CLEANUP_INTERVAL);
	}

	private void onCommit(AtomsCommittedToLedger atomsCommittedToLedger) {
		atomsCommittedToLedger.getTxns().forEach(txn -> updateStatus(txn.getId(), CONFIRMED));
	}

	private void onReject(MempoolAddFailure mempoolAddFailure) {
		updateStatus(mempoolAddFailure.getTxn().getId(), FAILED);
	}

	private void onSuccess(MempoolAddSuccess mempoolAddSuccess) {
		updateStatus(mempoolAddSuccess.getTxn().getId(), PENDING);
	}

	public void close() {
		disposable.dispose();
	}

	public TransactionStatus getTransactionStatus(AID txId) {
		return Optional.ofNullable(txCache.get(txId))
			.flatMap(TxStatusEntry::getStatus)
			.orElseGet(() -> store.contains(txId) ? CONFIRMED : TRANSACTION_NOT_FOUND);
	}

	public EventProcessor<ScheduledCacheCleanup> cacheCleanupProcessor() {
		return flush -> {
			cleanupCache();
			scheduledCacheCleanup.dispatch(ScheduledCacheCleanup.create(), DEFAULT_CLEANUP_INTERVAL);
		};
	}

	private void updateStatus(AID id, TransactionStatus newStatus) {
		txCache.compute(id, (key, value) ->
			Optional.ofNullable(value).orElseGet(this::createEntry).update(newStatus));
	}

	private TxStatusEntry createEntry() {
		return TxStatusEntry.create(this::clock);
	}

	@VisibleForTesting
	Instant clock() {
		return Instant.now();
	}

	private void cleanupCache() {
		var expirationTime = clock().minus(DEFAULT_TX_LIFE_TIME);

		var scheduledForRemoval = new HashSet<AID>();

		txCache.forEach((key, value) -> {
			var currentStatus = value.getStatus().orElse(PENDING);

			if (currentStatus != PENDING && value.isBefore(expirationTime)) {
				scheduledForRemoval.add(key);
			}
		});

		scheduledForRemoval.forEach(txCache::remove);
	}

	private static class TxStatusEntry {
		private final AtomicReference<Instant> timestamp = new AtomicReference<>();
		private final AtomicReference<TransactionStatus> status = new AtomicReference<>();
		private final Supplier<Instant> clock;

		private TxStatusEntry(Supplier<Instant> clock) {
			this.clock = clock;
			this.timestamp.set(clock.get());
		}

		static TxStatusEntry create(Supplier<Instant> clock) {
			return new TxStatusEntry(clock);
		}

		Optional<TransactionStatus> getStatus() {
			return Optional.ofNullable(status.get());
		}

		boolean isBefore(Instant expirationTime) {
			return timestamp.get().isBefore(expirationTime);
		}

		public TxStatusEntry update(TransactionStatus newStatus) {
			timestamp.set(clock.get());
			status.set(newStatus);
			return this;
		}
	}
}
