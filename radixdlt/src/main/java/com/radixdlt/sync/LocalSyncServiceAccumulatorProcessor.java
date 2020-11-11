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
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.ledger.AccumulatorState;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.ledger.LedgerUpdateProcessor;
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
public final class LocalSyncServiceAccumulatorProcessor implements LocalSyncServiceProcessor, LedgerUpdateProcessor<LedgerUpdate> {
	public static final class SyncInProgress {
		private final VerifiedLedgerHeaderAndProof targetHeader;
		private final ImmutableList<BFTNode> targetNodes;
		private SyncInProgress(VerifiedLedgerHeaderAndProof targetHeader, ImmutableList<BFTNode> targetNodes) {
			this.targetHeader = targetHeader;
			this.targetNodes = targetNodes;
		}

		private ImmutableList<BFTNode> getTargetNodes() {
			return targetNodes;
		}

		private VerifiedLedgerHeaderAndProof getTargetHeader() {
			return targetHeader;
		}
	}

	public interface SyncTimeoutScheduler {
		void scheduleTimeout(SyncInProgress syncInProgress, long milliseconds);
	}

	private static final Logger log = LogManager.getLogger();

	private final SyncTimeoutScheduler syncTimeoutScheduler;
	private final long patienceMilliseconds;
	private final StateSyncNetworkSender stateSyncNetworkSender;
	private final Comparator<AccumulatorState> accComparator;
	private VerifiedLedgerHeaderAndProof targetHeader;
	private VerifiedLedgerHeaderAndProof currentHeader;

	public LocalSyncServiceAccumulatorProcessor(
		StateSyncNetworkSender stateSyncNetworkSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		Comparator<AccumulatorState> accComparator,
		VerifiedLedgerHeaderAndProof current,
		long patienceMilliseconds
	) {
		if (patienceMilliseconds <= 0) {
			throw new IllegalArgumentException();
		}

		this.stateSyncNetworkSender = Objects.requireNonNull(stateSyncNetworkSender);
		this.syncTimeoutScheduler = Objects.requireNonNull(syncTimeoutScheduler);
		this.patienceMilliseconds = patienceMilliseconds;
		this.accComparator = Objects.requireNonNull(accComparator);
		this.currentHeader = current;
		this.targetHeader = current;
	}

	@Override
	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
		if (accComparator.compare(updatedHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) > 0) {
			this.currentHeader = updatedHeader;
		}
	}

	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor() {
		return this::processLocalSyncRequest;
	}

	private void processLocalSyncRequest(LocalSyncRequest request) {
		log.info("SYNC_LOCAL_REQUEST: {}", request);

		final VerifiedLedgerHeaderAndProof nextTargetHeader = request.getTarget();
		if (accComparator.compare(nextTargetHeader.getAccumulatorState(), this.targetHeader.getAccumulatorState()) <= 0) {
			return;
		}

		this.targetHeader = nextTargetHeader;
		SyncInProgress syncInProgress = new SyncInProgress(request.getTarget(), request.getTargetNodes());
		this.refreshRequest(syncInProgress);
	}

	@Override
	public void processSyncTimeout(SyncInProgress syncInProgress) {
		this.refreshRequest(syncInProgress);
	}

	private void refreshRequest(SyncInProgress syncInProgress) {
		VerifiedLedgerHeaderAndProof requestTargetHeader = syncInProgress.getTargetHeader();
		if (accComparator.compare(requestTargetHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState()) <= 0) {
			return;
		}

		ImmutableList<BFTNode> targetNodes = syncInProgress.getTargetNodes();
		BFTNode node = targetNodes.get(ThreadLocalRandom.current().nextInt(targetNodes.size()));
		stateSyncNetworkSender.sendSyncRequest(node, this.currentHeader.toDto());
		syncTimeoutScheduler.scheduleTimeout(syncInProgress, patienceMilliseconds);
	}
}
