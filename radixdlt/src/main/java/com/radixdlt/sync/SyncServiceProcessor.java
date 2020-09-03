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
import com.radixdlt.consensus.LedgerState;
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.VerifiedCommittedCommands;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Processes sync service messages and manages sync requests and responses.
 * Thread-safety must be handled by caller.
 */
@NotThreadSafe
public final class SyncServiceProcessor {
	public interface SyncedCommandSender {
		void sendSyncedCommand(VerifiedCommittedCommands committedCommand);
	}

	public static final class SyncInProgress {
		private final VerifiedCommittedHeader targetHeader;
		private final List<BFTNode> targetNodes;
		private SyncInProgress(VerifiedCommittedHeader targetHeader, List<BFTNode> targetNodes) {
			this.targetHeader = targetHeader;
			this.targetNodes = targetNodes;
		}

		private List<BFTNode> getTargetNodes() {
			return targetNodes;
		}

		private VerifiedCommittedHeader getTargetHeader() {
			return targetHeader;
		}
	}

	public interface SyncTimeoutScheduler {
		void scheduleTimeout(SyncInProgress syncInProgress, long milliseconds);
	}

	private static final Logger log = LogManager.getLogger();
	private static final int MAX_REQUESTS_TO_SEND = 20;
	private final RadixEngineStateComputer stateComputer;
	private final SyncedCommandSender syncedCommandSender;
	private final int batchSize;
	private final SyncTimeoutScheduler syncTimeoutScheduler;
	private final long patienceMilliseconds;
	private final AddressBook addressBook;
	private final StateSyncNetwork stateSyncNetwork;
	private VerifiedCommittedHeader targetHeader;
	private LedgerState currentState;

	public SyncServiceProcessor(
		RadixEngineStateComputer stateComputer,
		StateSyncNetwork stateSyncNetwork,
		AddressBook addressBook,
		SyncedCommandSender syncedCommandSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		VerifiedCommittedHeader current,
		int batchSize,
		long patienceMilliseconds
	) {
		if (patienceMilliseconds <= 0) {
			throw new IllegalArgumentException();
		}
		if (batchSize <= 0) {
			throw new IllegalArgumentException();
		}
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.syncedCommandSender = Objects.requireNonNull(syncedCommandSender);
		this.syncTimeoutScheduler = Objects.requireNonNull(syncTimeoutScheduler);
		this.batchSize = batchSize;
		this.patienceMilliseconds = patienceMilliseconds;
		this.currentState = current.getLedgerState();
		this.targetHeader = current;
	}

	public void processSyncRequest(SyncRequest syncRequest) {
		log.debug("SYNC_REQUEST: {}", syncRequest);
		Peer peer = syncRequest.getPeer();
		long stateVersion = syncRequest.getStateVersion();
		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		try {
			VerifiedCommittedCommands committedCommands = stateComputer.getNextCommittedCommands(stateVersion, batchSize);
			if (committedCommands == null) {
				return;
			}

			log.debug("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedCommands.getCommands().size());
			stateSyncNetwork.sendSyncResponse(peer, committedCommands);
		} catch (NextCommittedLimitReachedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public void processSyncResponse(VerifiedCommittedCommands commands) {
		final LedgerState responseLedgerState = commands.getProof().getLedgerState();
		if (responseLedgerState.compareTo(this.currentState) <= 0) {
			return;
		}

		// TODO: Check validity of response
		this.syncedCommandSender.sendSyncedCommand(commands.truncateFromVersion(this.currentState.getStateVersion()));
		this.currentState = responseLedgerState;
	}

	public void processVersionUpdate(LedgerState updatedCurrentState) {
		if (updatedCurrentState.compareTo(this.currentState) > 0) {
			this.currentState = updatedCurrentState;
		}
	}

	// TODO: Handle epoch changes with same state version
	public void processLocalSyncRequest(LocalSyncRequest request) {
		final VerifiedCommittedHeader targetHeader = request.getTarget();
		final LedgerState targetLedgerState = targetHeader.getLedgerState();
		if (targetLedgerState.compareTo(this.targetHeader.getLedgerState()) <= 0) {
			return;
		}

		this.targetHeader = targetHeader;
		SyncInProgress syncInProgress = new SyncInProgress(request.getTarget(), request.getTargetNodes());
		this.sendRequests(syncInProgress);
	}

	public void processSyncTimeout(SyncInProgress syncInProgress) {
		this.sendRequests(syncInProgress);
	}

	private void sendRequests(SyncInProgress syncInProgress) {
		final LedgerState targetLedgerState = syncInProgress.getTargetHeader().getLedgerState();
		if (targetLedgerState.compareTo(this.currentState) <= 0) {
			return;
		}

		if (targetLedgerState.getStateVersion() == this.currentState.getStateVersion()) {
			// Already command synced just need to update header
			// TODO: Move this to a more appropriate place
			VerifiedCommittedCommands verifiedCommittedCommands = new VerifiedCommittedCommands(
				ImmutableList.of(),
				syncInProgress.getTargetHeader()
			);
			this.syncedCommandSender.sendSyncedCommand(verifiedCommittedCommands);
			return;
		}

		sendSyncRequest(syncInProgress.getTargetNodes());
		syncTimeoutScheduler.scheduleTimeout(syncInProgress, patienceMilliseconds);
	}

	private void sendSyncRequest(List<BFTNode> targetNodes) {
		List<Peer> peers = targetNodes.stream()
			.map(BFTNode::getKey)
			.map(pk -> addressBook.peer(pk.euid()))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.filter(Peer::hasSystem)
			.collect(Collectors.toList());
		// TODO: Remove this exception
		if (peers.isEmpty()) {
			throw new IllegalStateException("Unable to find peer");
		}
		Peer peer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));

		final long version = this.currentState.getStateVersion();
		stateSyncNetwork.sendSyncRequest(peer, version);
	}
}
