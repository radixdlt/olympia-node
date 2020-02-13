/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.tempo;

import com.google.inject.Inject;
import com.radixdlt.common.AID;
import com.radixdlt.common.Atom;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.ConsensusObservation;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.engine.AtomEventListener;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A three-chain BFT
 */
public final class ChainedBFT implements Consensus {
	private static final Logger log = Logging.getLogger("bft");
	private static final int INBOUND_QUEUE_CAPACITY = 16384;

	private final BlockingQueue<ConsensusObservation> consensusObservations;
	private final Universe universe;
	private final LedgerEntryStore store;
	private final RadixEngine radixEngine;

	@Inject
	public ChainedBFT(
		EventCoordinator eventCoordinator,
		MemPool memPool,
		Universe universe,
		RadixEngine radixEngine,
		LedgerEntryStore store,
		DumbPacemaker dumbPacemaker
	) {
		Objects.requireNonNull(memPool);
		Objects.requireNonNull(eventCoordinator);
		Objects.requireNonNull(universe);
		Objects.requireNonNull(store);
		Objects.requireNonNull(radixEngine);

		this.universe = universe;
		this.store = store;
		this.radixEngine = radixEngine;

		this.consensusObservations = new LinkedBlockingQueue<>(INBOUND_QUEUE_CAPACITY);

		dumbPacemaker.addCallback(eventCoordinator::newRound);

		this.initGenesis();
	}

	@Override
	public ConsensusObservation observe() throws InterruptedException {
		return this.consensusObservations.take();
	}

	private void initGenesis() {
		try {
			LinkedList<AID> atomIds = new LinkedList<>();
			for (Atom atom : universe.getGenesis()) {
				if (!store.contains(atom.getAID())) {
					radixEngine.store(atom,
						new AtomEventListener() {
							@Override
							public void onCMSuccess(Atom atom) {
								log.debug("Genesis Atom " + atom.getAID() + " stored to atom store");
							}

							@Override
							public void onCMError(Atom atom, CMError error) {
								log.fatal("Failed to addAtom genesis Atom: " + error.getErrorCode() + " "
									+ error.getErrMsg() + " " + error.getDataPointer() + "\n"
									+ atom + "\n"
									+ error.getCmValidationState().toString());
								System.exit(-1);
							}

							@Override
							public void onVirtualStateConflict(Atom atom, DataPointer dp) {
								log.fatal("Failed to addAtom genesis Atom: Virtual State Conflict");
								System.exit(-1);
							}

							@Override
							public void onStateConflict(Atom atom, DataPointer dp, Atom conflictAtom) {
								log.fatal("Failed to addAtom genesis Atom: State Conflict");
								System.exit(-1);
							}

							@Override
							public void onStateMissingDependency(AID atomId, Particle particle) {
								log.fatal("Failed to addAtom genesis Atom: Missing Dependency");
								System.exit(-1);
							}
						});
				}
			}
			waitForAtoms(atomIds);
		} catch (Exception ex) {
			log.fatal("Failed to addAtom genesis Atom", ex);
			System.exit(-1);
		}
	}

	private void waitForAtoms(List<AID> atomHashes) throws InterruptedException {
		for (AID atomID : atomHashes) {
			while (!store.contains(atomID)) {
				TimeUnit.MILLISECONDS.sleep(100);
			}
		}
	}
}
