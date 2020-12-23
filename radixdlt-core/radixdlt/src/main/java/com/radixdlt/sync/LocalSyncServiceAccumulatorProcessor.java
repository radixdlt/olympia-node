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
		private final ImmutableList<BFTNode> targetNodes;
		private SyncInProgress(VerifiedLedgerHeaderAndProof targetHeader, ImmutableList<BFTNode> targetNodes) {
			this.targetHeader = targetHeader;
			this.targetNodes = targetNodes;
		}

		public ImmutableList<BFTNode> getTargetNodes() {
			return targetNodes;
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
	}

	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
		}
		systemCounters.set(CounterType.SYNC_TARGET_CURRENT_DIFF, this.targetHeader.getStateVersion() - this.currentHeader.getStateVersion());
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		log.info("SYNC_LOCAL_REQUEST: {} current {}", request, this.currentHeader);

		final VerifiedLedgerHeaderAndProof nextTargetHeader = request.getTarget();
		if (accComparator.compare(nextTargetHeader.getAccumulatorState(), this.targetHeader.getAccumulatorState()) <= 0) {
			log.trace("SYNC_LOCAL_REQUEST: skipping as already targeted {}", this.targetHeader);
			return;
		}

		this.targetHeader = nextTargetHeader;
		SyncInProgress syncInProgress = new SyncInProgress(request.getTarget(), request.getTargetNodes());
		this.refreshRequest(syncInProgress);
	}

	public EventProcessor<SyncInProgress> syncTimeoutProcessor() {
		return this::refreshRequest;
	}

	private void refreshRequest(SyncInProgress syncInProgress) {
		VerifiedLedgerHeaderAndProof requestTargetHeader = syncInProgress.getTargetHeader();
		if (accComparator.compare(requestTargetHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) <= 0) {
			return;
		}

		systemCounters.set(CounterType.SYNC_TARGET_CURRENT_DIFF, requestTargetHeader.getStateVersion() - this.currentHeader.getStateVersion());

		ImmutableList<BFTNode> targetNodes = syncInProgress.getTargetNodes();
		// TODO: remove thread local random
		BFTNode node = targetNodes.get(ThreadLocalRandom.current().nextInt(targetNodes.size()));
		requestDispatcher.dispatch(node, this.currentHeader.toDto());
		timeoutScheduler.dispatch(syncInProgress, patienceMilliseconds);
	}
}
