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

package com.radixdlt.statecomputer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.consensus.Command;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.VertexMetadata;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.ClientAtom;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializationException;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.ledger.CommittedCommand;
import com.radixdlt.ledger.StateComputerLedger.StateComputer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineStateComputer implements StateComputer {

	// TODO: Refactor committed command when commit logic is re-written
	// TODO: as currently it's mostly loosely coupled logic
	public interface CommittedAtomWithResult {
		CommittedAtom getCommittedAtom();
		CommittedAtomWithResult ifSuccess(Consumer<ImmutableSet<EUID>> successConsumer);
		CommittedAtomWithResult ifError(Consumer<RadixEngineException> errorConsumer);
	}

	// TODO: Remove this temporary interface
	public interface CommittedAtomSender {
		void sendCommittedAtom(CommittedAtomWithResult committedAtomWithResult);
	}

	private final Serialization serialization;
	private final CommittedAtomsStore committedAtomsStore;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final Function<Long, BFTValidatorSet> validatorSetMapping;
	private final View epochChangeView;

	private final CommittedAtomSender committedAtomSender;
	private final Object lock = new Object();
	private final LinkedList<CommittedCommand> unstoredCommittedAtoms = new LinkedList<>();

	public RadixEngineStateComputer(
		Serialization serialization,
		RadixEngine<LedgerAtom> radixEngine,
		Function<Long, BFTValidatorSet> validatorSetMapping,
		View epochChangeView,
		CommittedAtomsStore committedAtomsStore,
		CommittedAtomSender committedAtomSender
	) {
		if (epochChangeView.isGenesis()) {
			throw new IllegalArgumentException("Epoch change view must not be genesis.");
		}

		this.serialization = Objects.requireNonNull(serialization);
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.validatorSetMapping = validatorSetMapping;
		this.epochChangeView = epochChangeView;
		this.committedAtomsStore = Objects.requireNonNull(committedAtomsStore);
		this.committedAtomSender = Objects.requireNonNull(committedAtomSender);
	}

	// TODO Move this to a different class class when unstored committed atoms is fixed
	public List<CommittedCommand> getCommittedCommands(long stateVersion, int batchSize) {
		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		// TODO: remove 100 hardcode limit
		List<CommittedCommand> storedCommittedAtoms = committedAtomsStore.getCommittedCommands(stateVersion, batchSize);

		// TODO: Remove
		final List<CommittedCommand> copy;
		synchronized (lock) {
			copy = new ArrayList<>(unstoredCommittedAtoms);
		}

		return Streams.concat(
			storedCommittedAtoms.stream(),
			copy.stream().filter(a -> a.getVertexMetadata().getStateVersion() > stateVersion)
		)
			.sorted(Comparator.comparingLong(a -> a.getVertexMetadata().getStateVersion()))
			.collect(ImmutableList.toImmutableList());
	}

	@Override
	public void commit(Command command, VertexMetadata vertexMetadata) {
		if (command != null) {
			final ClientAtom clientAtom;
			try {
				clientAtom = serialization.fromDson(command.getPayload(), ClientAtom.class);
			} catch (SerializationException e) {
				this.unstoredCommittedAtoms.add(new CommittedCommand(null, vertexMetadata));
				return;
			}

			CommittedAtom committedAtom = new CommittedAtom(clientAtom, vertexMetadata);

			try {
				// TODO: execute list of commands instead
				this.radixEngine.checkAndStore(committedAtom);

				// TODO: cleanup and move this logic to a better spot
				final ImmutableSet<EUID> indicies = committedAtomsStore.getIndicies(committedAtom);

				committedAtomSender.sendCommittedAtom(CommittedAtoms.success(committedAtom, indicies));
			} catch (RadixEngineException e) {
				// TODO: Don't check for state computer errors for now so that we don't
				// TODO: have to deal with failing leader proposals
				// TODO: Reinstate this when ProposalGenerator + Mempool can guarantee correct proposals

				// TODO: move VIRTUAL_STATE_CONFLICT to static check
				this.unstoredCommittedAtoms.add(new CommittedCommand(command, vertexMetadata));

				committedAtomSender.sendCommittedAtom(CommittedAtoms.error(committedAtom, e));
			}
		} else if (vertexMetadata.isEndOfEpoch()) {
			// TODO: HACK
			// TODO: Remove and move epoch change logic into RadixEngine
			this.unstoredCommittedAtoms.add(new CommittedCommand(null, vertexMetadata));
		} else {
			// TODO: HACK
			// TODO: Refactor to remove such illegal states
			throw new IllegalStateException("Should never get here " + command);
		}
	}

	@Override
	public Optional<BFTValidatorSet> prepare(Vertex vertex) {
		if (vertex.getView().compareTo(epochChangeView) >= 0) {
			return Optional.of(validatorSetMapping.apply(vertex.getEpoch() + 1));
		} else {
			return Optional.empty();
		}
	}
}
