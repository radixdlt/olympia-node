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
import com.radixdlt.consensus.VerifiedCommittedHeader;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.VerifiedCommittedCommand;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.store.berkeley.NextCommittedLimitReachedException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
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
		void sendSyncedCommand(VerifiedCommittedCommand committedCommand);
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
	private final int maxAtomsQueueSize;
	private final SyncTimeoutScheduler syncTimeoutScheduler;
	private final long patienceMilliseconds;
	private final AddressBook addressBook;
	private final StateSyncNetwork stateSyncNetwork;
	private final TreeSet<VerifiedCommittedCommand> committedCommands = new TreeSet<>(
		Comparator.comparingLong(a -> a.getProof().getLedgerState().getStateVersion())
	);

	private VerifiedCommittedHeader targetHeader;
	private long currentVersion;

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
		if (currentVersion < 0) {
			throw new IllegalArgumentException(String.format("current version must be >= 0 but was %s", currentVersion));
		}
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
		// we limit the size of the queue in order to avoid memory issues
		this.maxAtomsQueueSize = MAX_REQUESTS_TO_SEND * batchSize * 2;

		this.currentVersion = current.getLedgerState().getStateVersion();
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
			List<VerifiedCommittedCommand> committedCommands = stateComputer.getCommittedCommands(stateVersion, batchSize);

			log.debug("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedCommands.size());
			stateSyncNetwork.sendSyncResponse(peer, committedCommands);
		} catch (NextCommittedLimitReachedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public void processSyncResponse(ImmutableList<VerifiedCommittedCommand> commands) {
		// TODO: Check validity of response
		log.debug("SYNC_RESPONSE: size: {}", commands.size());
		for (VerifiedCommittedCommand command : commands) {
			long stateVersion = command.getProof().getLedgerState().getStateVersion();
			if (stateVersion > this.currentVersion) {
				if (committedCommands.size() < maxAtomsQueueSize) { // check if there is enough space
					committedCommands.add(command);
				} else { // not enough space available
					VerifiedCommittedCommand last = committedCommands.last();
					// will added it only if it must be applied BEFORE the most recent atom we have
					if (last.getProof().getLedgerState().getStateVersion() > stateVersion) {
						committedCommands.pollLast(); // remove the most recent available
						committedCommands.add(command);
					}
				}
			}
		}

		Iterator<VerifiedCommittedCommand> it = committedCommands.iterator();
		while (it.hasNext()) {
			VerifiedCommittedCommand command = it.next();
			long stateVersion = command.getProof().getLedgerState().getStateVersion();
			if (stateVersion <= currentVersion) {
				it.remove();
			} else if (stateVersion == currentVersion + 1) {
				this.syncedCommandSender.sendSyncedCommand(command);
				this.currentVersion = this.currentVersion + 1;
				it.remove();
			} else {
				break;
			}
		}
	}

	public void processVersionUpdate(long updatedCurrentVersion) {
		if (updatedCurrentVersion > this.currentVersion) {
			this.currentVersion = updatedCurrentVersion;
		}
	}

	public void processLocalSyncRequest(LocalSyncRequest request) {
		final VerifiedCommittedHeader targetHeader = request.getTarget();
		final long targetVersionRequest = targetHeader.getLedgerState().getStateVersion();
		if (targetVersionRequest <= this.targetHeader.getLedgerState().getStateVersion()) {
			return;
		}

		this.targetHeader = targetHeader;
		SyncInProgress syncInProgress = new SyncInProgress(request.getTarget(), request.getTargetNodes());
		this.sendRequests(syncInProgress);
	}

	public void processSyncTimeout(SyncInProgress syncInProgress) {
		final long targetVersion = syncInProgress.getTargetHeader().getLedgerState().getStateVersion();
		if (targetVersion <= currentVersion) {
			return;
		}

		this.sendRequests(syncInProgress);
	}

	private void sendRequests(SyncInProgress syncInProgress) {
		sendSyncRequest(currentVersion, syncInProgress.getTargetNodes());
		syncTimeoutScheduler.scheduleTimeout(syncInProgress, patienceMilliseconds);
	}

	private void sendSyncRequest(long version, List<BFTNode> targetNodes) {
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
		stateSyncNetwork.sendSyncRequest(peer, version);
	}
}
