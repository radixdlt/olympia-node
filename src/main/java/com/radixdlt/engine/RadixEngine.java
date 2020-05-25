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

package com.radixdlt.engine;

import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateMachine;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<T extends RadixEngineAtom> {
	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;
	private final EngineStore<T> engineStore;
	private final AtomChecker<T> checker;
	private final Object stateUpdateEngineLock = new Object();

	public RadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<T> engineStore
	) {
		this(constraintMachine, virtualStoreLayer, engineStore, null);
	}

	public RadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore<T> engineStore,
		AtomChecker<T> checker
	) {
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.virtualizedCMStore = virtualStoreLayer.apply(CMStores.empty());
		this.engineStore = Objects.requireNonNull(engineStore);
		this.checker = checker;
	}

	public void staticCheck(T atom) throws RadixEngineException {
		final Optional<CMError> error = constraintMachine.validate(atom.getCMInstruction());
		if (error.isPresent()) {
			throw new RadixEngineException(RadixEngineErrorCode.CM_ERROR, error.get().getDataPointer());
		}

		if (checker != null) {
			Result hookResult = checker.check(atom);
			if (hookResult.isError()) {
				throw new RadixEngineException(RadixEngineErrorCode.HOOK_ERROR, DataPointer.ofAtom());
			}
		}
	}

	/**
	 * Atomically stores the given atom into the store. If the atom
	 * has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param atom the atom to store
	 * @throws RadixEngineException on state conflict or dependency issues
	 */
	public void checkAndStore(T atom) throws RadixEngineException {
		this.staticCheck(atom);

		synchronized (stateUpdateEngineLock) {
			// TODO Feature: Return updated state for some given query (e.g. for current validator set)
			stateCheckAndStoreInternal(atom);
		}
	}

	private void stateCheckAndStoreInternal(T atom) throws RadixEngineException {
		final CMInstruction cmInstruction = atom.getCMInstruction();

		long particleIndex = 0;
		long particleGroupIndex = 0;
		for (CMMicroInstruction microInstruction : cmInstruction.getMicroInstructions()) {
			// Treat check spin as the first push for now
			if (!microInstruction.isCheckSpin()) {
				if (microInstruction.getMicroOp() == CMMicroOp.PARTICLE_GROUP) {
					particleGroupIndex++;
					particleIndex = 0;
				} else {
					particleIndex++;
				}
				continue;
			}

			final Particle particle = microInstruction.getParticle();
			if (!engineStore.supports(particle.getDestinations())) {
				continue;
			}

			final DataPointer dp = DataPointer.ofParticle(particleGroupIndex, particleIndex);

			// First spin is the only one we need to check
			final Spin checkSpin = microInstruction.getCheckSpin();
			final Spin virtualSpin = virtualizedCMStore.getSpin(particle);
			// TODO: Move virtual state checks into static check
			if (SpinStateMachine.isBefore(checkSpin, virtualSpin)) {
				throw new RadixEngineException(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT, dp);
			}

			final Spin nextSpin = SpinStateMachine.next(checkSpin);
			final Spin physicalSpin = engineStore.getSpin(particle);
			final Spin currentSpin = SpinStateMachine.isAfter(virtualSpin, physicalSpin) ? virtualSpin : physicalSpin;
			if (!SpinStateMachine.canTransition(currentSpin, nextSpin)) {
				if (!SpinStateMachine.isBefore(currentSpin, nextSpin)) {
					// Hack for now
					// TODO: replace blocking callback with rx
					final AtomicReference<T> conflictAtom = new AtomicReference<>();
					engineStore.getAtomContaining(particle, nextSpin == Spin.DOWN, conflictAtom::set);
					throw new RadixEngineException(RadixEngineErrorCode.STATE_CONFLICT, dp, conflictAtom.get());
				} else {
					throw new RadixEngineException(RadixEngineErrorCode.MISSING_DEPENDENCY, dp);
				}
			}
		}

		engineStore.storeAtom(atom);
	}
}
