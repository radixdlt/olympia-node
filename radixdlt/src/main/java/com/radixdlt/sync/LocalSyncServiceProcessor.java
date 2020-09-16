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
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.LedgerUpdate;
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
	public interface DtoCommandsAndProofVerifier {
		VerifiedCommandsAndProof verify(DtoCommandsAndProof dtoCommandsAndProof) throws DtoCommandsAndProofVerifierException;
	}

	public static class DtoCommandsAndProofVerifierException extends Exception {
		private final DtoCommandsAndProof dtoCommandsAndProof;
		public DtoCommandsAndProofVerifierException(DtoCommandsAndProof dtoCommandsAndProof, String message) {
			super(message);
			this.dtoCommandsAndProof = dtoCommandsAndProof;
		}

		public DtoCommandsAndProof getDtoCommandsAndProof() {
			return dtoCommandsAndProof;
		}
	}

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
	private final Comparator<VerifiedLedgerHeaderAndProof> headerComparator;
	private final InvalidSyncedCommandsSender invalidSyncedCommandsSender;
	private final DtoCommandsAndProofVerifier verifier;
	private VerifiedLedgerHeaderAndProof targetHeader;
	private VerifiedLedgerHeaderAndProof currentHeader;

	public LocalSyncServiceProcessor(
		StateSyncNetwork stateSyncNetwork,
		VerifiedSyncedCommandsSender verifiedSyncedCommandsSender,
		InvalidSyncedCommandsSender invalidSyncedCommandsSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		DtoCommandsAndProofVerifier verifier,
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
		this.verifier = Objects.requireNonNull(verifier);
		this.headerComparator = Objects.requireNonNull(headerComparator);
		this.currentHeader = current;
		this.targetHeader = current;
	}

	public void processSyncResponse(DtoCommandsAndProof commandsAndProof) {
		log.info("SYNC_RESPONSE: {} current={} target={}", commandsAndProof, this.currentHeader, this.targetHeader);

		VerifiedCommandsAndProof verified;
		try {
			verified = this.verifier.verify(commandsAndProof);
		} catch (DtoCommandsAndProofVerifierException e) {
			log.warn("SYNC_RESPONSE Verification failed {}: {}", commandsAndProof, e.getMessage());
			invalidSyncedCommandsSender.sendInvalidCommands(commandsAndProof);
			return;
		}

		// TODO: Check validity of response
		this.verifiedSyncedCommandsSender.sendVerifiedCommands(verified);
	}

	public void processLedgerUpdate(LedgerUpdate ledgerUpdate) {
		VerifiedLedgerHeaderAndProof updatedHeader = ledgerUpdate.getTail();
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
		this.refreshRequest(syncInProgress);
	}

	public void processSyncTimeout(SyncInProgress syncInProgress) {
		this.refreshRequest(syncInProgress);
	}

	private void refreshRequest(SyncInProgress syncInProgress) {
		VerifiedLedgerHeaderAndProof requestTargetHeader = syncInProgress.getTargetHeader();
		if (headerComparator.compare(requestTargetHeader, this.currentHeader) <= 0) {
			return;
		}

		if (Objects.equals(requestTargetHeader.getAccumulatorState(), this.currentHeader.getAccumulatorState())) {
			// Already command synced just need to update header
			// TODO: Need to check epochs to make sure we're not skipping epochs
			VerifiedCommandsAndProof commandsAndProof = new VerifiedCommandsAndProof(
				ImmutableList.of(),
				requestTargetHeader
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
