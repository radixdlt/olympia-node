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

package com.radixdlt.consensus.sync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.EpochChangeSender;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
	public interface SyncedRadixEngineEventSender {
		void sendStored(CommittedAtom committedAtom, ImmutableSet<EUID> indicies);
		void sendStoredFailure(CommittedAtom committedAtom, RadixEngineException e);
	}

	private static final int BATCH_SIZE = 100;

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	private static final Logger log = LogManager.getLogger();
	private final Mempool mempool;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final CommittedAtomsStore committedAtomsStore;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final EpochChangeSender epochChangeSender;
	private final SyncedRadixEngineEventSender engineEventSender;
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final AddressBook addressBook;
	private final StateSyncNetwork stateSyncNetwork;
	private final View epochChangeView;
	private final SystemCounters counters;
	private final SyncManager syncManager;

	// TODO: Remove the following
	private final Object lock = new Object();
	private final LinkedList<CommittedAtom> unstoredCommittedAtoms = new LinkedList<>();
	private final Subject<CommittedAtom> lastStoredAtom = BehaviorSubject.create();
	private VertexMetadata lastEpochChange = null;

	public SyncedRadixEngine(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		SyncedRadixEngineEventSender engineEventSender,
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
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.committedAtomsStore = Objects.requireNonNull(committedAtomsStore);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.epochChangeSender = Objects.requireNonNull(epochChangeSender);
		this.engineEventSender = Objects.requireNonNull(engineEventSender);
		this.validatorSetMapping = validatorSetMapping;
		this.epochChangeView = epochChangeView;
		this.addressBook = Objects.requireNonNull(addressBook);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
		this.counters = Objects.requireNonNull(counters);

		this.syncManager = new SyncManager(this::execute, this.committedAtomsStore::getStateVersion, BATCH_SIZE, 10);
	}

	/**
	 * Start the service
	 */
	public void start() {
		stateSyncNetwork.syncRequests()
			.observeOn(Schedulers.io())
			.subscribe(syncRequest -> {
				log.debug("SYNC_REQUEST: {} currentStateVersion={}", syncRequest, this.committedAtomsStore.getStateVersion());
				Peer peer = syncRequest.getPeer();
				long stateVersion = syncRequest.getStateVersion();
				// TODO: This may still return an empty list as we still count state versions for atoms which
				// TODO: never make it into the radix engine due to state errors. This is because we only check
				// TODO: validity on commit rather than on proposal/prepare.
				// TODO: remove 100 hardcode limit
				List<CommittedAtom> storedCommittedAtoms = committedAtomsStore.getCommittedAtoms(stateVersion, BATCH_SIZE);

				// TODO: Remove
				final List<CommittedAtom> copy;
				synchronized (lock) {
					copy = new ArrayList<>(unstoredCommittedAtoms);
				}

				List<CommittedAtom> committedAtoms = Streams.concat(
					storedCommittedAtoms.stream(),
					copy.stream().filter(a -> a.getVertexMetadata().getStateVersion() > stateVersion)
				)
					.sorted(Comparator.comparingLong(a -> a.getVertexMetadata().getStateVersion()))
					.collect(ImmutableList.toImmutableList());

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
		final long currentStateVersion = committedAtomsStore.getStateVersion();
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

	private void handleRadixEngineException(CommittedAtom atom, RadixEngineException e) {
		// TODO: Don't check for state computer errors for now so that we don't
		// TODO: have to deal with failing leader proposals
		// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

		// TODO: move VIRTUAL_STATE_CONFLICT to static check
		engineEventSender.sendStoredFailure(atom, e);
	}

	/**
	 * Add an atom to the committed store
	 * @param atom the atom to commit
	 */
	@Override
	public void execute(CommittedAtom atom) {
		// TODO: remove lock
		synchronized (lock) {
			counters.increment(CounterType.LEDGER_PROCESSED);

			final long stateVersion = atom.getVertexMetadata().getStateVersion();

			if (stateVersion != 0 && stateVersion <= committedAtomsStore.getStateVersion()) {
				return;
			}

			counters.set(CounterType.LEDGER_STATE_VERSION, stateVersion);

			// TODO: HACK
			// TODO: Remove and move epoch change logic into RadixEngine
			committedAtomsStore.storeVertexMetadata(atom.getVertexMetadata());

			if (atom.getClientAtom() != null) {
				try {
					// TODO: execute list of commands instead
					this.radixEngine.checkAndStore(atom);

					// TODO: cleanup and move this logic to a better spot
					final ImmutableSet<EUID> indicies = committedAtomsStore.getIndicies(atom);
					this.engineEventSender.sendStored(atom, indicies);

				} catch (RadixEngineException e) {
					handleRadixEngineException(atom, e);
					this.unstoredCommittedAtoms.add(atom);
				}

				this.lastStoredAtom.onNext(atom);
				this.mempool.removeCommittedAtom(atom.getAID());
			} else if (atom.getVertexMetadata().isEndOfEpoch()) {
				// TODO: HACK
				// TODO: Remove and move epoch change logic into RadixEngine
				this.unstoredCommittedAtoms.add(atom);
				this.lastStoredAtom.onNext(atom);
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
