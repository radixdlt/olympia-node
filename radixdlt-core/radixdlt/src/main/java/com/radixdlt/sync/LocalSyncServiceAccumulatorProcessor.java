/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.sync;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.store.LastProof;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes sync service messages and manages sync requests and responses.
 * Thread-safety must be handled by caller.
 */
@NotThreadSafe
public final class LocalSyncServiceAccumulatorProcessor {
	public static final class SyncInProgress {
		private final VerifiedLedgerHeaderAndProof targetHeader;
		private final VerifiedLedgerHeaderAndProof currentHeader;
		private final ImmutableList<BFTNode> targetNodes;
		private SyncInProgress(
			VerifiedLedgerHeaderAndProof targetHeader,
			VerifiedLedgerHeaderAndProof currentHeader,
			ImmutableList<BFTNode> targetNodes
		) {
			this.targetHeader = targetHeader;
			this.currentHeader = currentHeader;
			this.targetNodes = targetNodes;
		}

		public ImmutableList<BFTNode> getTargetNodes() {
			return targetNodes;
		}

		public VerifiedLedgerHeaderAndProof getCurrentHeader() {
			return currentHeader;
		}

		public VerifiedLedgerHeaderAndProof getTargetHeader() {
			return targetHeader;
		}
	}

	private static final Logger log = LogManager.getLogger();

	private final ScheduledEventDispatcher<SyncInProgress> timeoutScheduler;
	private final long patienceMilliseconds;
	private final RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher;
	private final Comparator<AccumulatorState> accComparator;
	private final SystemCounters systemCounters;
	private VerifiedLedgerHeaderAndProof targetHeader;
	private VerifiedLedgerHeaderAndProof currentHeader;
	private ImmutableList<BFTNode> targetNodes;


	@Inject
	public LocalSyncServiceAccumulatorProcessor(
		RemoteEventDispatcher<DtoLedgerHeaderAndProof> requestDispatcher,
		ScheduledEventDispatcher<SyncInProgress> timeoutScheduler,
		Comparator<AccumulatorState> accComparator,
		@LastProof VerifiedLedgerHeaderAndProof current,
		@SyncPatienceMillis int patienceMilliseconds,
		SystemCounters systemCounters
	) {
		if (patienceMilliseconds <= 0) {
			throw new IllegalArgumentException();
		}

		this.requestDispatcher = Objects.requireNonNull(requestDispatcher);
		this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler);
		this.systemCounters = Objects.requireNonNull(systemCounters);
		this.patienceMilliseconds = patienceMilliseconds;
		this.accComparator = Objects.requireNonNull(accComparator);
		this.currentHeader = current;
		this.targetHeader = current;
		this.targetNodes = ImmutableList.of();
	}

	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
			updateCounters();
			if (accComparator.compare(this.targetHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
				final var nextSync = new SyncInProgress(this.targetHeader, this.currentHeader, this.targetNodes);
				timeoutScheduler.dispatch(nextSync, 0);
			}
		}
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		log.debug("SYNC_LOCAL_REQUEST: {} current {}", request, this.currentHeader);

		final VerifiedLedgerHeaderAndProof nextTargetHeader = request.getTarget();
		if (accComparator.compare(nextTargetHeader.getAccumulatorState(), this.targetHeader.getAccumulatorState()) <= 0) {
			log.trace("SYNC_LOCAL_REQUEST: skipping as already targeted {}", this.targetHeader);
			return;
		}

		this.targetHeader = nextTargetHeader;
		this.targetNodes = request.getTargetNodes();
		updateCounters();
		SyncInProgress syncInProgress = new SyncInProgress(this.targetHeader, this.currentHeader, this.targetNodes);
		this.refreshRequest(syncInProgress, "start");
	}

	public EventProcessor<SyncInProgress> syncTimeoutProcessor() {
		return sip -> refreshRequest(sip, "timeout");
	}

	private void refreshRequest(SyncInProgress syncInProgress, String what) {
		ImmutableList<BFTNode> targetNodes = syncInProgress.getTargetNodes();
		if (targetNodes.isEmpty()) {
			// Can't really do anything in this case
			return;
		}
		if (this.currentHeader != syncInProgress.getCurrentHeader()) {
			// This request already satisfied
			return;
		}
		VerifiedLedgerHeaderAndProof requestTargetHeader = syncInProgress.getTargetHeader();
		if (accComparator.compare(requestTargetHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) <= 0) {
			return;
		}

		log.debug("RefreshRequest: {} for current={}, target={}", what, syncInProgress.currentHeader, syncInProgress.targetHeader);

		// TODO: remove thread local random
		BFTNode node = targetNodes.get(ThreadLocalRandom.current().nextInt(targetNodes.size()));
		requestDispatcher.dispatch(node, this.currentHeader.toDto());
		timeoutScheduler.dispatch(syncInProgress, patienceMilliseconds);
	}

	private void updateCounters() {
		systemCounters.set(
			CounterType.SYNC_TARGET_CURRENT_DIFF,
			this.targetHeader.getStateVersion() - this.currentHeader.getStateVersion()
		);
	}
}
