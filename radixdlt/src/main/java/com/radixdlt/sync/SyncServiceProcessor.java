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
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.statecomputer.RadixEngineStateComputer;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
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
		void sendSyncedCommand(CommittedCommand committedCommand);
	}

	public static final class SyncInProgress {
		private final long id;
		private final List<BFTNode> target;
		private SyncInProgress(long id, List<BFTNode> target) {
			this.id = id;
			this.target = target;
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
	private final TreeSet<CommittedCommand> committedCommands = new TreeSet<>(
		Comparator.comparingLong(a -> a.getVertexMetadata().getPreparedCommand().getStateVersion())
	);

	private long syncInProgressId = 0;
	private boolean isSyncInProgress = false;
	private long syncToTargetVersion;
	private long currentVersion;

	public SyncServiceProcessor(
		RadixEngineStateComputer stateComputer,
		StateSyncNetwork stateSyncNetwork,
		AddressBook addressBook,
		SyncedCommandSender syncedCommandSender,
		SyncTimeoutScheduler syncTimeoutScheduler,
		long currentVersion,
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
		this.currentVersion = currentVersion;
		this.syncToTargetVersion = currentVersion;
		this.stateComputer = Objects.requireNonNull(stateComputer);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.syncedCommandSender = Objects.requireNonNull(syncedCommandSender);
		this.syncTimeoutScheduler = Objects.requireNonNull(syncTimeoutScheduler);
		this.batchSize = batchSize;
		this.patienceMilliseconds = patienceMilliseconds;
		// we limit the size of the queue in order to avoid memory issues
		this.maxAtomsQueueSize = MAX_REQUESTS_TO_SEND * batchSize * 2;
	}

	public void processSyncRequest(SyncRequest syncRequest) {
		log.debug("SYNC_REQUEST: {}", syncRequest);
		Peer peer = syncRequest.getPeer();
		long stateVersion = syncRequest.getStateVersion();
		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		List<CommittedCommand> committedCommands = stateComputer.getCommittedCommands(stateVersion, batchSize);
		log.debug("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedCommands.size());
		stateSyncNetwork.sendSyncResponse(peer, committedCommands);
	}

	public void processSyncResponse(ImmutableList<CommittedCommand> commands) {
		// TODO: Check validity of response
		log.debug("SYNC_RESPONSE: size: {}", commands.size());
		for (CommittedCommand command : commands) {
			long stateVersion = command.getVertexMetadata().getPreparedCommand().getStateVersion();
			if (stateVersion > this.currentVersion) {
				if (committedCommands.size() < maxAtomsQueueSize) { // check if there is enough space
					committedCommands.add(command);
				} else { // not enough space available
					CommittedCommand last = committedCommands.last();
					// will added it only if it must be applied BEFORE the most recent atom we have
					if (last.getVertexMetadata().getPreparedCommand().getStateVersion() > stateVersion) {
						committedCommands.pollLast(); // remove the most recent available
						committedCommands.add(command);
					}
				}
			}
		}

		Iterator<CommittedCommand> it = committedCommands.iterator();
		while (it.hasNext()) {
			CommittedCommand command = it.next();
			long stateVersion = command.getVertexMetadata().getPreparedCommand().getStateVersion();
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

		// TODO: Need to check if this response actually corresponds to sync-in-progress
		isSyncInProgress = false;
	}

	public void processVersionUpdate(long updatedCurrentVersion) {
		if (updatedCurrentVersion > this.currentVersion) {
			this.currentVersion = updatedCurrentVersion;
		}
	}

	public void processLocalSyncRequest(LocalSyncRequest request) {
		final VertexMetadata target = request.getTarget();
		if (target.getPreparedCommand().getStateVersion() <= this.currentVersion) {
			return;
		}
		this.syncToTargetVersion = target.getPreparedCommand().getStateVersion();
		sendSyncRequests(request.getTargetNodes());
	}

	public void processSyncTimeout(SyncInProgress syncInProgress) {
		if (syncInProgress.id == syncInProgressId && isSyncInProgress) {
			this.sendSyncRequests(syncInProgress.target);
		}
	}

	private void sendSyncRequests(List<BFTNode> target) {
		if (currentVersion >= syncToTargetVersion) {
			return;
		}
		long size = ((syncToTargetVersion - currentVersion) / batchSize);
		if ((syncToTargetVersion - currentVersion) % batchSize > 0) {
			size += 1;
		}
		size = Math.min(size, MAX_REQUESTS_TO_SEND);
		for (long i = 0; i < size; i++) {
			sendSyncRequest(currentVersion + batchSize * i, target);
		}

		syncInProgressId++;
		isSyncInProgress = true;
		SyncInProgress syncInProgress = new SyncInProgress(syncInProgressId, target);
		syncTimeoutScheduler.scheduleTimeout(syncInProgress, patienceMilliseconds);
	}

	private void sendSyncRequest(long version, List<BFTNode> target) {
		List<Peer> peers = target.stream()
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
