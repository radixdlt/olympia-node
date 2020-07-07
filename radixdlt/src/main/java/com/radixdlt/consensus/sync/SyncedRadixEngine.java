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
import com.radixdlt.consensus.EpochChange;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.validators.ValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineErrorCode;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.Peer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.atoms.AtomDependencyNotFoundException;
import org.radix.atoms.events.AtomExceptionEvent;
import org.radix.atoms.particles.conflict.ParticleConflict;
import org.radix.atoms.particles.conflict.ParticleConflictException;
import org.radix.events.Events;
import org.radix.validation.ConstraintMachineValidationException;

/**
 * A service which synchronizes the radix engine committed state between peers.
 *
 * TODO: Most of the logic here should go into RadixEngine itself
 */
public final class SyncedRadixEngine implements SyncedStateComputer<CommittedAtom> {

	public interface CommittedStateSyncSender {
		void sendCommittedStateSync(long stateVersion, Object opaque);
	}

	private static final Logger log = LogManager.getLogger();
	private final RadixEngine<LedgerAtom> radixEngine;
	private final CommittedAtomsStore committedAtomsStore;
	private final CommittedStateSyncSender committedStateSyncSender;
	private final EpochChangeSender epochChangeSender;
	private final Function<Long, ValidatorSet> validatorSetMapping;
	private final AddressBook addressBook;
	private final StateSyncNetwork stateSyncNetwork;
	private final View epochChangeView;

	// TODO: Remove the following
	private final Object lock = new Object();
	private final LinkedList<CommittedAtom> emptyCommittedAtoms = new LinkedList<>();
	private VertexMetadata lastEpochChange = null;

	public SyncedRadixEngine(
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		Function<Long, ValidatorSet> validatorSetMapping,
		View epochChangeView,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.committedAtomsStore = Objects.requireNonNull(committedAtomsStore);
		this.committedStateSyncSender = Objects.requireNonNull(committedStateSyncSender);
		this.epochChangeSender = Objects.requireNonNull(epochChangeSender);
		this.validatorSetMapping = validatorSetMapping;
		this.epochChangeView = epochChangeView;
		this.addressBook = Objects.requireNonNull(addressBook);
		this.stateSyncNetwork = Objects.requireNonNull(stateSyncNetwork);
	}

	/**
	 * Start the service
	 */
	public void start() {
		stateSyncNetwork.syncRequests()
			.observeOn(Schedulers.io())
			.subscribe(syncRequest -> {
				log.info("SYNC_REQUEST: {} currentStateVersion={}", syncRequest, this.committedAtomsStore.getStateVersion());
				Peer peer = syncRequest.getPeer();
				long stateVersion = syncRequest.getStateVersion();
				// TODO: This may still return an empty list as we still count state versions for atoms which
				// TODO: never make it into the radix engine due to state errors. This is because we only check
				// TODO: validity on commit rather than on proposal/prepare.
				// TODO: remove 100 hardcode limit
				List<CommittedAtom> storedCommittedAtoms = committedAtomsStore.getCommittedAtoms(stateVersion, 100);

				// TODO: Remove
				final List<CommittedAtom> copy;
				synchronized (lock) {
					copy = new ArrayList<>(emptyCommittedAtoms);
				}

				List<CommittedAtom> committedAtoms = Streams.concat(
					storedCommittedAtoms.stream(),
					copy.stream().filter(a -> a.getVertexMetadata().getStateVersion() > stateVersion)
				)
					.sorted(Comparator.comparingLong(a -> a.getVertexMetadata().getStateVersion()))
					.collect(ImmutableList.toImmutableList());

				log.info("SYNC_REQUEST: SENDING_RESPONSE size: {}", committedAtoms.size());
				stateSyncNetwork.sendSyncResponse(peer, committedAtoms);
			});

		stateSyncNetwork.syncResponses()
			.observeOn(Schedulers.io())
			.subscribe(syncResponse -> {
				// TODO: Check validity of response
				log.info("SYNC_RESPONSE: size: {}", syncResponse.size());
				for (CommittedAtom committedAtom : syncResponse) {
					if (committedAtom.getVertexMetadata().getStateVersion() > this.committedAtomsStore.getStateVersion()) {
						this.execute(committedAtom);
					}
				}
			});
	}

	@Override
	public boolean syncTo(VertexMetadata vertexMetadata, List<ECPublicKey> target, Object opaque) {
		if (target.isEmpty()) {
			// TODO: relax this in future when we have non-validator nodes
			throw new IllegalArgumentException("target must not be empty");
		}

		final long targetStateVersion = vertexMetadata.getStateVersion();
		final long currentStateVersion = committedAtomsStore.getStateVersion();
		if (targetStateVersion <= currentStateVersion) {
			return true;
		}

		// TODO: better randomization of peer selection
		Peer peer = target.stream()
			.map(pk -> addressBook.peer(pk.euid()))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.filter(Peer::hasSystem)
			.findFirst()
			.orElseThrow(() -> new RuntimeException("Unable to find peer"));
		stateSyncNetwork.sendSyncRequest(peer, currentStateVersion);

		committedAtomsStore.lastStoredAtom()
			.observeOn(Schedulers.io())
			.map(e -> e.getAtom().getVertexMetadata().getStateVersion())
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
			// TODO: HACK
			// TODO: Remove and move epoch change logic into RadixEngine
			committedAtomsStore.storeVertexMetadata(atom.getVertexMetadata());

			if (atom.getClientAtom() != null) {
				try {
					// TODO: execute list of commands instead
					this.radixEngine.checkAndStore(atom);
				} catch (RadixEngineException e) {
					// TODO: Don't check for state computer errors for now so that we don't
					// TODO: have to deal with failing leader proposals
					// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

					// TODO: move VIRTUAL_STATE_CONFLICT to static check
					if (e.getErrorCode() == RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT) {
						ConstraintMachineValidationException exception
							= new ConstraintMachineValidationException(atom.getClientAtom(), "Virtual state conflict", e.getDataPointer());
						Events.getInstance().broadcast(new AtomExceptionEvent(exception, atom.getAID()));
					} else if (e.getErrorCode() == RadixEngineErrorCode.STATE_CONFLICT) {
						final ParticleConflictException conflict = new ParticleConflictException(
							new ParticleConflict(e.getDataPointer(), ImmutableSet.of(atom.getAID(), e.getRelated().getAID())
							));
						AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(conflict, atom.getAID());
						Events.getInstance().broadcast(atomExceptionEvent);
					} else if (e.getErrorCode() == RadixEngineErrorCode.MISSING_DEPENDENCY) {
						final AtomDependencyNotFoundException notFoundException =
							new AtomDependencyNotFoundException(
								String.format("Atom has missing dependencies in transitions: %s", e.getDataPointer().toString()),
								e.getDataPointer()
							);

						AtomExceptionEvent atomExceptionEvent = new AtomExceptionEvent(notFoundException, atom.getAID());
						Events.getInstance().broadcast(atomExceptionEvent);
					}
				}
			} else if (atom.getVertexMetadata().isEndOfEpoch()) {
				// TODO: HACK
				// TODO: Remove and move epoch change logic into RadixEngine
				if (emptyCommittedAtoms.isEmpty()
					|| emptyCommittedAtoms.getLast().getVertexMetadata().getStateVersion() != atom.getVertexMetadata().getStateVersion()) {
					emptyCommittedAtoms.add(atom);
				}
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
