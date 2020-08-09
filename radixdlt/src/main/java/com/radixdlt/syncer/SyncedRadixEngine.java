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

package com.radixdlt.syncer;

import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.execution.RadixEngineExecutor;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A service which synchronizes the radix engine committed state between peers.
 *
 * TODO: Most of the logic here should go into RadixEngine itself
 */
public final class SyncedRadixEngine implements SyncedStateComputer<CommittedAtom> {
	private static final int BATCH_SIZE = 100;

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	private static final Logger log = LogManager.getLogger();
	private final Mempool mempool;
	private final RadixEngineExecutor executor;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final EpochChangeSender epochChangeSender;
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final AddressBook addressBook;
	private final StateSyncNetwork stateSyncNetwork;
	private final View epochChangeView;
	private final SystemCounters counters;
	private final SyncManager syncManager;

	// TODO: Remove the following
	private final Object lock = new Object();
	private final Subject<CommittedAtom> lastStoredAtom = BehaviorSubject.create();
	private final AtomicLong stateVersion = new AtomicLong(0);
	private VertexMetadata lastEpochChange = null;

	public SyncedRadixEngine(
		Mempool mempool,
		RadixEngineExecutor executor,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		View epochChangeView,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork,
		SystemCounters counters
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.mempool = Objects.requireNonNull(mempool);
		this.executor = Objects.requireNonNull(executor);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.epochChangeSender = Objects.requireNonNull(epochChangeSender);
		this.validatorSetMapping = validatorSetMapping;
		this.epochChangeView = epochChangeView;
		this.addressBook = Objects.requireNonNull(addressBook);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.counters = Objects.requireNonNull(counters);

		this.syncManager = new SyncManager(this::execute, this.stateVersion::get, BATCH_SIZE, 10);
	}

	/**
	 * Start the service
	 */
	public void start() {
		stateSyncNetwork.syncRequests()
			.observeOn(Schedulers.io())
			.subscribe(syncRequest -> {
				log.debug("SYNC_REQUEST: {} currentStateVersion={}", syncRequest, this.stateVersion.get());
				Peer peer = syncRequest.getPeer();
				long stateVersion = syncRequest.getStateVersion();
				// TODO: This may still return an empty list as we still count state versions for atoms which
				// TODO: never make it into the radix engine due to state errors. This is because we only check
				// TODO: validity on commit rather than on proposal/prepare.
				// TODO: remove 100 hardcode limit
				List<CommittedAtom> committedAtoms = executor.getCommittedAtoms(stateVersion, BATCH_SIZE);
				log.debug("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedAtoms.size());
				stateSyncNetwork.sendSyncResponse(peer, committedAtoms);
			});

		stateSyncNetwork.syncResponses()
			.observeOn(Schedulers.io())
			.subscribe(syncResponse -> {
				// TODO: Check validity of response
				log.debug("SYNC_RESPONSE: size: {}", syncResponse.size());
				syncManager.syncAtoms(syncResponse);
			});
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, List<BFTNode> target, Object opaque) {
		if (target.isEmpty()) {
			// TODO: relax this in future when we have non-validator nodes
			throw new IllegalArgumentException("target must not be empty");
		}

		final long targetStateVersion = vertexMetadata.getStateVersion();
		final long currentStateVersion = this.stateVersion.get();
		if (targetStateVersion <= currentStateVersion) {
			return true;
		}

		syncManager.syncToVersion(targetStateVersion, version -> {
			List<Peer> peers = target.stream()
					.map(BFTNode::getKey)
					.map(pk -> addressBook.peer(pk.euid()))
					.filter(Optional::isPresent)
					.map(Optional::get)
					.filter(Peer::hasSystem)
					.collect(Collectors.toList());
			if (peers.isEmpty()) {
				throw new IllegalStateException("Unable to find peer");
			}
			Peer peer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
			stateSyncNetwork.sendSyncRequest(peer, version);
		});

		this.lastStoredAtom
			.observeOn(Schedulers.io())
			.map(atom -> atom.getVertexMetadata().getStateVersion())
			.filter(stateVersion -> stateVersion >= targetStateVersion)
			.firstOrError()
			.ignoreElement()
			.subscribe(() -> committedStateSyncSender.sendCommittedStateSync(targetStateVersion, opaque));

		return false;
	}

	@Override
	public boolean compute(Vertex vertex) {
		return vertex.getView().compareTo(epochChangeView) >= 0;
	}

	/**
	 * Add an atom to the committed store
	 * @param atom the atom to commit
	 */
	@Override
	public void execute(CommittedAtom atom) {
		// TODO: remove lock
		synchronized (lock) {
			this.counters.increment(CounterType.LEDGER_PROCESSED);

			final long stateVersion = atom.getVertexMetadata().getStateVersion();

			if (stateVersion != 0 && stateVersion <= this.stateVersion.get()) {
				return;
			}

			this.stateVersion.set(stateVersion);
			this.counters.set(CounterType.LEDGER_STATE_VERSION, stateVersion);

			this.executor.execute(atom);
			this.lastStoredAtom.onNext(atom);

			if (atom.getClientAtom() != null) {
				this.mempool.removeCommittedAtom(atom.getAID());
			}

			// TODO: Move outside of syncedRadixEngine to a more generic syncing layer
			if (atom.getVertexMetadata().isEndOfEpoch()
				&& (lastEpochChange == null || lastEpochChange.getEpoch() != atom.getVertexMetadata().getEpoch())) {

				VertexMetadata ancestor = atom.getVertexMetadata();
				this.lastEpochChange = ancestor;
				EpochChange epochChange = new EpochChange(ancestor, validatorSetMapping.apply(ancestor.getEpoch() + 1));
				this.epochChangeSender.epochChange(epochChange);
			}
		}
	}
}
