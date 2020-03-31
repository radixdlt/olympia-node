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
import com.radixdlt.atommodel.Atom;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction.CMMicroOp;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.middleware.RadixEngineUtils;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.SpinStateMachine;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine {

	private static final Logger log = LogManager.getLogger(RadixEngine.class);

	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;
	private final EngineStore engineStore;
	private final CopyOnWriteArrayList<AtomEventListener> atomEventListeners = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<CMSuccessHook> cmSuccessHooks = new CopyOnWriteArrayList<>();
	private final Object stateUpdateEngineLock = new Object();

	public RadixEngine(
		ConstraintMachine constraintMachine,
		UnaryOperator<CMStore> virtualStoreLayer,
		EngineStore engineStore
	) {
		this.constraintMachine = constraintMachine;
		// Remove cm virtual store
		this.virtualizedCMStore = virtualStoreLayer.apply(CMStores.empty());
		this.engineStore = engineStore;
	}

	public void addCMSuccessHook(CMSuccessHook hook) {
		this.cmSuccessHooks.add(hook);
	}

	public void addAtomEventListener(AtomEventListener acceptor) {
		this.atomEventListeners.add(acceptor);
	}

	public Optional<CMError> staticCheck(Atom atom) {
		RadixEngineAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			return Optional.of(new CMError(e.getDataPointer(), CMErrorCode.INVALID_PARTICLE, null));
		}
		return constraintMachine.validate(cmAtom.getCMInstruction());
	}

	public void store(Atom atom) throws RadixEngineException {
		Objects.requireNonNull(atom);

		RadixEngineAtom cmAtom;
		try {
			cmAtom = RadixEngineUtils.toCMAtom(atom);
		} catch (RadixEngineUtils.CMAtomConversionException e) {
			log.error("Atom creation failed", e);
			CMError cmError = new CMError(e.getDataPointer(), CMErrorCode.INVALID_PARTICLE, null);
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, cmError));
			throw new RadixEngineException(RadixEngineErrorCode.CONVERSION_ERROR, e.getDataPointer());
		}

		final Optional<CMError> error = constraintMachine.validate(cmAtom.getCMInstruction());
		if (error.isPresent()) {
			log.error("Atom is not valid: {}", error.get());
			this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, error.get()));
			throw new RadixEngineException(RadixEngineErrorCode.CM_ERROR, error.get().getDataPointer());
		}

		for (CMSuccessHook hook : cmSuccessHooks) {
			Result hookResult = hook.hook(atom);
			if (hookResult.isError()) {
				CMError cmError = new CMError(DataPointer.ofAtom(), CMErrorCode.HOOK_ERROR, null, hookResult.getErrorMessage());
				this.atomEventListeners.forEach(acceptor -> acceptor.onCMError(atom, cmError));
				throw new RadixEngineException(RadixEngineErrorCode.HOOK_ERROR, DataPointer.ofAtom());
			}
		}

		this.atomEventListeners.forEach(acceptor -> acceptor.onCMSuccess(atom));

		synchronized (stateUpdateEngineLock) {
			stateCheckAndStore(atom, cmAtom);
		}
	}

	private void stateCheckAndStore(Atom atom, RadixEngineAtom cmAtom) throws RadixEngineException {
		final CMInstruction cmInstruction = cmAtom.getCMInstruction();

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

			// First spun is the only one we need to check
			final Spin checkSpin = microInstruction.getCheckSpin();
			final Spin virtualSpin = virtualizedCMStore.getSpin(particle);
			if (SpinStateMachine.isBefore(checkSpin, virtualSpin)) {
				atomEventListeners.forEach(listener -> listener.onVirtualStateConflict(atom, dp));
				throw new RadixEngineException(RadixEngineErrorCode.VIRTUAL_STATE_CONFLICT, dp);
			}

			final Spin nextSpin = SpinStateMachine.next(checkSpin);
			final Spin physicalSpin = engineStore.getSpin(particle);
			final Spin currentSpin = SpinStateMachine.isAfter(virtualSpin, physicalSpin) ? virtualSpin : physicalSpin;
			if (!SpinStateMachine.canTransition(currentSpin, nextSpin)) {
				if (!SpinStateMachine.isBefore(currentSpin, nextSpin)) {
					engineStore.getAtomContaining(particle, nextSpin == Spin.DOWN, conflictAtom -> {
						atomEventListeners.forEach(listener -> listener.onStateConflict(atom, dp, conflictAtom));
					});

					throw new RadixEngineException(RadixEngineErrorCode.STATE_CONFLICT, dp);
				} else {
					atomEventListeners.forEach(listener -> listener.onStateMissingDependency(atom.getAID(), particle));
					throw new RadixEngineException(RadixEngineErrorCode.MISSING_DEPENDENCY, dp);
				}
			}
		}

		engineStore.storeAtom(atom);
		atomEventListeners.forEach(listener -> listener.onStateStore(atom));
	}
}
