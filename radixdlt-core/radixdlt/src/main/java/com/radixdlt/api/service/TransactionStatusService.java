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

package com.radixdlt.api.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.api.data.TransactionStatus;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;
import com.radixdlt.store.berkeley.BerkeleyLedgerEntryStore;
import com.radixdlt.utils.functional.Result;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.reactivex.rxjava3.disposables.CompositeDisposable;

import static com.radixdlt.api.data.TransactionStatus.CONFIRMED;
import static com.radixdlt.api.data.TransactionStatus.FAILED;
import static com.radixdlt.api.data.TransactionStatus.PENDING;
import static com.radixdlt.api.data.TransactionStatus.TRANSACTION_NOT_FOUND;

//TODO: finish refactoring, move lookup transaction method here
public class TransactionStatusService {
	private static final long DEFAULT_CLEANUP_INTERVAL = 1000L;                        //every second
	private static final Duration DEFAULT_TX_LIFE_TIME = Duration.ofMinutes(10);    //at most 10 minutes

	private final CompositeDisposable disposable = new CompositeDisposable();
	private final ConcurrentMap<AID, TxStatusEntry> txCache = new ConcurrentHashMap<>();
	private final BerkeleyLedgerEntryStore store;
	private final ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup;
	private final ClientApiStore clientApiStore;

	@Inject
	public TransactionStatusService(
		BerkeleyLedgerEntryStore store,
		ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup,
		ClientApiStore clientApiStore
	) {
		this.store = store;
		this.scheduledCacheCleanup = scheduledCacheCleanup;
		this.clientApiStore = clientApiStore;

		scheduledCacheCleanup.dispatch(ScheduledCacheCleanup.create(), DEFAULT_CLEANUP_INTERVAL);
	}

	private void onCommit(TxnsCommittedToLedger txnsCommittedToLedger) {
		txnsCommittedToLedger.getParsedTxs().forEach(txn -> updateStatus(txn.getTxn().getId(), CONFIRMED));
	}

	private void onReject(MempoolAddFailure mempoolAddFailure) {
		updateStatus(mempoolAddFailure.getTxn().getId(), FAILED);
	}

	private void onSuccess(MempoolAddSuccess mempoolAddSuccess) {
		updateStatus(mempoolAddSuccess.getTxn().getId(), PENDING);
	}

	public EventProcessor<TxnsCommittedToLedger> atomsCommittedToLedgerEventProcessor() {
		return this::onCommit;
	}

	public EventProcessor<MempoolAddFailure> mempoolAddFailureEventProcessor() {
		return this::onReject;
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddSuccessEventProcessor() {
		return this::onSuccess;
	}

	public void close() {
		disposable.dispose();
	}

	public Result<TxHistoryEntry> getTransaction(AID txId) {
		return clientApiStore.getTransaction(txId);
	}

	public TransactionStatus getTransactionStatus(AID txId) {
		return Optional.ofNullable(txCache.get(txId))
			.flatMap(TxStatusEntry::getStatus)
			.orElseGet(() -> store.contains(txId) ? CONFIRMED : TRANSACTION_NOT_FOUND);
	}

	public EventProcessor<ScheduledCacheCleanup> cacheCleanupEventProcessor() {
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
