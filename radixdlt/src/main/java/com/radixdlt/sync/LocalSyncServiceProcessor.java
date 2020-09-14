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
import com.radixdlt.crypto.Hash;
import com.radixdlt.ledger.LedgerAccumulator;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.VerifiedCommandsAndProof;
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
public final class LocalSyncServiceProcessor {
	public interface VerifiedSyncedCommandsSender {
		void sendVerifiedCommands(VerifiedCommandsAndProof commandsAndProof);
	}

	public interface InvalidSyncedCommandsSender {
		void sendInvalidCommands(DtoCommandsAndProof commandsAndProof);
	}

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
	private final VerifiedSyncedCommandsSender verifiedSyncedCommandsSender;
	private final SyncTimeoutScheduler syncTimeoutScheduler;
	private final long patienceMilliseconds;
	private final StateSyncNetwork stateSyncNetwork;
	private final LedgerAccumulator accumulator;
	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private VerifiedLedgerHeaderAndProof targetHeader;
	private VerifiedLedgerHeaderAndProof currentHeader;

	public LocalSyncServiceProcessor(
		StateSyncNetwork stateSyncNetwork,
		VerifiedSyncedCommandsSender verifiedSyncedCommandsSender,
		InvalidSyncedCommandsSender invalidSyncedCommandsSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		LedgerAccumulator accumulator,
		Comparator<VerifiedLedgerHeaderAndProof> headerComparator,
		VerifiedLedgerHeaderAndProof current,
		long patienceMilliseconds
	) {
		if (patienceMilliseconds <= 0) {
			throw new IllegalArgumentException();
		}

		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.verifiedSyncedCommandsSender = Objects.requireNonNull(verifiedSyncedCommandsSender);
		this.invalidSyncedCommandsSender = Objects.requireNonNull(invalidSyncedCommandsSender);
		this.syncTimeoutScheduler = Objects.requireNonNull(syncTimeoutScheduler);
		this.patienceMilliseconds = patienceMilliseconds;
		this.accumulator = Objects.requireNonNull(accumulator);
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentHeader = current;
		this.targetHeader = current;
	}

	public void processSyncResponse(DtoCommandsAndProof commandsAndProof) {
		log.info("SYNC_RESPONSE: {} current={} target={}", commandsAndProof, this.currentHeader, this.targetHeader);
		Hash currentAccumulated = commandsAndProof.getStartHeader().getLedgerHeader().getAccumulator();
		Hash nextAccumulated = this.accumulator.accumulate(currentAccumulated, commandsAndProof.getCommands());

		if (!commandsAndProof.getEndHeader().getLedgerHeader().getAccumulator().equals(nextAccumulated)) {
			log.warn("SYNC Received Bad commands: {}", commandsAndProof);
			invalidSyncedCommandsSender.sendInvalidCommands(commandsAndProof);
			return;
		}

		// TODO: Stateful ledger header verification:
		// TODO: -Check epoch signatures
		// TODO: -verify rootHash matches

		VerifiedLedgerHeaderAndProof nextHeader = new VerifiedLedgerHeaderAndProof(
			commandsAndProof.getEndHeader().getOpaque0(),
			commandsAndProof.getEndHeader().getOpaque1(),
			commandsAndProof.getEndHeader().getOpaque2(),
			commandsAndProof.getEndHeader().getOpaque3(),
			commandsAndProof.getEndHeader().getLedgerHeader(),
			commandsAndProof.getEndHeader().getSignatures()
		);

		VerifiedCommandsAndProof verified = new VerifiedCommandsAndProof(
			commandsAndProof.getCommands(),
			nextHeader
		);

		// TODO: Check validity of response
		this.verifiedSyncedCommandsSender.sendVerifiedCommands(verified);
	}

	public void processVersionUpdate(VerifiedLedgerHeaderAndProof updatedHeader) {
		if (headerComparator.compare(updatedHeader, this.currentHeader) > 0) {
			this.currentHeader = updatedHeader;
		}
	}

	// TODO: Handle epoch changes with same state version
	public void processLocalSyncRequest(LocalSyncRequest request) {
		log.info("SYNC_LOCAL_REQUEST: {}", request);

		final VerifiedLedgerHeaderAndProof nextTargetHeader = request.getTarget();
		if (headerComparator.compare(nextTargetHeader, this.targetHeader) <= 0) {
			return;
		}

		this.targetHeader = nextTargetHeader;
		SyncInProgress syncInProgress = new SyncInProgress(request.getTarget(), request.getTargetNodes());
		this.sendRequest(syncInProgress);
	}

	public void processSyncTimeout(SyncInProgress syncInProgress) {
		this.sendRequest(syncInProgress);
	}

	private void sendRequest(SyncInProgress syncInProgress) {
		if (headerComparator.compare(syncInProgress.getTargetHeader(), this.currentHeader) <= 0) {
			return;
		}

		if (syncInProgress.getTargetHeader().getStateVersion() == this.currentHeader.getStateVersion()) {
			// Already command synced just need to update header
			// TODO: Need to check epochs to make sure we're not skipping epochs
			VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
				ImmutableList.of(),
				syncInProgress.getTargetHeader()
			);
			this.verifiedSyncedCommandsSender.sendVerifiedCommands(commandsAndProof);
			return;
		}

		ImmutableList<BFTNode> targetNodes = syncInProgress.getTargetNodes();
		BFTNode node = targetNodes.get(ThreadLocalRandom.current().nextInt(targetNodes.size()));
		stateSyncNetwork.sendSyncRequest(node, this.currentHeader.toDto());
		syncTimeoutScheduler.scheduleTimeout(syncInProgress, patienceMilliseconds);
	}
}
