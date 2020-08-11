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

package com.radixdlt.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Wraps the Radix Engine and emits messages based on success or failure
 */
public final class RadixEngineExecutor {
	public interface RadixEngineExecutorEventSender {
		void sendStored(CommittedAtom committedAtom, ImmutableSet<EUID> indicies);
		void sendStoredFailure(CommittedAtom committedAtom, RadixEngineException e);
	}

	private final CommittedAtomsStore committedAtomsStore;
	private final RadixEngine<LedgerAtom> radixEngine;

	private final Object lock = new Object();
	private final LinkedList<CommittedAtom> unstoredCommittedAtoms = new LinkedList<>();
	private final RadixEngineExecutorEventSender engineEventSender;

	public RadixEngineExecutor(
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		RadixEngineExecutorEventSender engineEventSender
	) {
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.committedAtomsStore = Objects.requireNonNull(committedAtomsStore);
		this.engineEventSender = Objects.requireNonNull(engineEventSender);
	}

	public List<CommittedAtom> getCommittedAtoms(long stateVersion, int batchSize) {
		// TODO: This may still return an empty list as we still count state versions for atoms which
		// TODO: never make it into the radix engine due to state errors. This is because we only check
		// TODO: validity on commit rather than on proposal/prepare.
		// TODO: remove 100 hardcode limit
		List<CommittedAtom> storedCommittedAtoms = committedAtomsStore.getCommittedAtoms(stateVersion, batchSize);

		// TODO: Remove
		final List<CommittedAtom> copy;
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
	public void execute(CommittedAtom atom) {
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

		} else if (atom.getVertexMetadata().isEndOfEpoch()) {
			// TODO: HACK
			// TODO: Remove and move epoch change logic into RadixEngine
			this.unstoredCommittedAtoms.add(atom);
		}
	}
}
