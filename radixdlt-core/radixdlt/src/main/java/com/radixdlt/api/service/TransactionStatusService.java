/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.radixdlt.api.data.TransactionStatus;
import com.radixdlt.api.data.TxHistoryEntry;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.berkeley.BerkeleyTransactionsByIdStore;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.identifiers.AID;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.REOutput;
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

@Singleton
public class TransactionStatusService {
	private static final long DEFAULT_CLEANUP_INTERVAL = 1000L;                        //every second
	private static final Duration DEFAULT_TX_LIFE_TIME = Duration.ofMinutes(10);    //at most 10 minutes

	private final ConcurrentMap<AID, TxStatusEntry> txCache = new ConcurrentHashMap<>();
	private final BerkeleyTransactionsByIdStore store;
	private final ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup;
	private final ClientApiStore clientApiStore;

	@Inject
	public TransactionStatusService(
		BerkeleyTransactionsByIdStore store,
		ScheduledEventDispatcher<ScheduledCacheCleanup> scheduledCacheCleanup,
		ClientApiStore clientApiStore
	) {
		this.store = store;
		this.scheduledCacheCleanup = scheduledCacheCleanup;
		this.clientApiStore = clientApiStore;

		scheduledCacheCleanup.dispatch(ScheduledCacheCleanup.create(), DEFAULT_CLEANUP_INTERVAL);
	}

	private void onCommit(LedgerUpdate ledgerUpdate) {
		var output = ledgerUpdate.getStateComputerOutput().getInstance(REOutput.class);
		output.getProcessedTxns().forEach(txn -> updateStatus(txn.getTxn().getId(), CONFIRMED));
	}

	private void onReject(MempoolAddFailure mempoolAddFailure) {
		updateStatus(mempoolAddFailure.getTxn().getId(), FAILED);
	}

	private void onSuccess(MempoolAddSuccess mempoolAddSuccess) {
		updateStatus(mempoolAddSuccess.getTxn().getId(), PENDING);
	}

	public EventProcessor<LedgerUpdate> ledgerUpdateProcessor() {
		return this::onCommit;
	}

	public EventProcessor<MempoolAddFailure> mempoolAddFailureEventProcessor() {
		return this::onReject;
	}

	public EventProcessor<MempoolAddSuccess> mempoolAddSuccessEventProcessor() {
		return this::onSuccess;
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
