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

import com.google.common.hash.HashCode;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;

import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<T extends RadixEngineAtom> {
	private static class ApplicationStateComputer<U, V extends Particle, T extends RadixEngineAtom> {
		private final Class<V> particleClass;
		private final BiFunction<U, V, U> outputReducer;
		private final BiFunction<U, V, U> inputReducer;
		private final boolean includeInBranches;
		private U curValue;

		ApplicationStateComputer(
			Class<V> particleClass,
			U initialValue,
			BiFunction<U, V, U> outputReducer,
			BiFunction<U, V, U> inputReducer,
			boolean includeInBranches
		) {
			this.particleClass = particleClass;
			this.curValue = initialValue;
			this.outputReducer = outputReducer;
			this.inputReducer = inputReducer;
			this.includeInBranches = includeInBranches;
		}

		ApplicationStateComputer<U, V, T> copy() {
			return new ApplicationStateComputer<>(
				particleClass,
				curValue,
				outputReducer,
				inputReducer,
				includeInBranches
			);
		}

		void initialize(EngineStore<T> engineStore) {
			curValue = engineStore.compute(particleClass, curValue, outputReducer);
		}

		void processCheckSpin(Particle p, Spin checkSpin) {
			if (particleClass.isInstance(p)) {
				V particle = particleClass.cast(p);
				if (checkSpin == Spin.NEUTRAL) {
					curValue = outputReducer.apply(curValue, particle);
				} else {
					curValue = inputReducer.apply(curValue, particle);
				}
			}
		}
	}

	private final ConstraintMachine constraintMachine;
	private final CMStore virtualizedCMStore;
	private final Predicate<Particle> virtualStoreLayer;
	private final EngineStore<T> engineStore;
	private final AtomChecker<T> checker;
	private final Object stateUpdateEngineLock = new Object();
	private final Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, T>> stateComputers = new HashMap<>();
	private final List<RadixEngineBranch<T>> branches = new ArrayList<>();

	public RadixEngine(
		ConstraintMachine constraintMachine,
		Predicate<Particle> virtualStoreLayer,
		EngineStore<T> engineStore
	) {
		this(constraintMachine, virtualStoreLayer, engineStore, null);
	}

	public RadixEngine(
		ConstraintMachine constraintMachine,
		Predicate<Particle> virtualStoreLayer,
		EngineStore<T> engineStore,
		AtomChecker<T> checker
	) {
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.virtualStoreLayer = Objects.requireNonNull(virtualStoreLayer);
		this.virtualizedCMStore = new CMStore() {
			@Override
			public Spin getSpin(Particle particle) {
				var curSpin = engineStore.getSpin(particle);
				if (curSpin == Spin.DOWN) {
					return curSpin;
				}

				if (virtualStoreLayer.test(particle)) {
					return Spin.UP;
				}

				return curSpin;
			}

			@Override
			public Optional<Particle> loadUpParticle(HashCode particleHash) {
				return engineStore.loadUpParticle(particleHash);
			}
		};
		this.engineStore = Objects.requireNonNull(engineStore);
		this.checker = checker;
	}


	/**
	 * Add a deterministic computation engine which maps an ordered list of
	 * particles which have been created and destroyed to a state.
	 * Initially runs the computation with all the atoms currently in the store
	 * and then updates the state value as atoms get stored.
	 *
	 * @param stateReducer the reducer
	 * @param <U> the class of the state
	 * @param <V> the class of the particles to map
	 */
	public <U, V extends Particle> void addStateReducer(StateReducer<U, V> stateReducer, String name, boolean includeInBranches) {
		ApplicationStateComputer<U, V, T> applicationStateComputer = new ApplicationStateComputer<>(
			stateReducer.particleClass(),
			stateReducer.initial().get(),
			stateReducer.outputReducer(),
			stateReducer.inputReducer(),
			includeInBranches
		);

		synchronized (stateUpdateEngineLock) {
			applicationStateComputer.initialize(this.engineStore);
			stateComputers.put(Pair.of(stateReducer.stateClass(), name), applicationStateComputer);
		}
	}

	public <U, V extends Particle> void addStateReducer(StateReducer<U, V> stateReducer, boolean includeInBranches) {
		addStateReducer(stateReducer, null, includeInBranches);
	}

	/**
	 * Retrieves the latest state
	 * @param applicationStateClass the class of the state to retrieve
	 * @param <U> the class of the state to retrieve
	 * @return the current state
	 */
	public <U> U getComputedState(Class<U> applicationStateClass) {
		return getComputedState(applicationStateClass, null);
	}

	/**
	 * Retrieves the latest state
	 * @param applicationStateClass the class of the state to retrieve
	 * @param <U> the class of the state to retrieve
	 * @return the current state
	 */
	public <U> U getComputedState(Class<U> applicationStateClass, String name) {
		synchronized (stateUpdateEngineLock) {
			return applicationStateClass.cast(stateComputers.get(Pair.of(applicationStateClass, name)).curValue);
		}
	}


	/**
	 * A cheap radix engine branch which is purely transient
	 * @param <T> the type of engine atom
	 */
	public static class RadixEngineBranch<T extends RadixEngineAtom> {
		private final RadixEngine<T> engine;

		private RadixEngineBranch(
			ConstraintMachine constraintMachine,
			Predicate<Particle> virtualStoreLayer,
			EngineStore<T> parentStore,
			AtomChecker<T> checker,
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, T>> stateComputers
		) {
			TransientEngineStore<T> transientEngineStore = new TransientEngineStore<>(
				parentStore
			);

			this.engine = new RadixEngine<>(
				constraintMachine,
				virtualStoreLayer,
				transientEngineStore,
				checker
			);

			engine.stateComputers.putAll(stateComputers);
		}

		public void execute(T atom) throws RadixEngineException {
			engine.execute(atom);
		}

		public void execute(T atom, PermissionLevel permissionLevel) throws RadixEngineException {
			engine.execute(atom, permissionLevel);
		}

		public <U> U getComputedState(Class<U> applicationStateClass) {
			return engine.getComputedState(applicationStateClass);
		}
	}

	public void deleteBranches() {
		synchronized (stateUpdateEngineLock) {
			branches.clear();
		}
	}

	public RadixEngineBranch<T> transientBranch() {
		synchronized (stateUpdateEngineLock) {
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, T>> branchedStateComputers = new HashMap<>();
			this.stateComputers.forEach((c, computer) -> {
				if (computer.includeInBranches) {
					branchedStateComputers.put(c, computer.copy());
				}
			});
			RadixEngineBranch<T> branch = new RadixEngineBranch<>(
				this.constraintMachine,
				this.virtualStoreLayer,
				this.engineStore,
				this.checker,
				branchedStateComputers
			);

			branches.add(branch);

			return branch;
		}
	}

	private HashMap<HashCode, Particle> verify(T atom, PermissionLevel permissionLevel) throws RadixEngineException {
		var downedParticles = new HashMap<HashCode, Particle>();
		final Optional<CMError> error = constraintMachine.validate(
			virtualizedCMStore,
			atom.getCMInstruction(),
			atom.getWitness(),
			permissionLevel,
			downedParticles
		);

		if (error.isPresent()) {
			CMError e = error.get();
			throw new RadixEngineException(atom, RadixEngineErrorCode.CM_ERROR, e.getErrorDescription(), e.getDataPointer(), e);
		}

		if (checker != null) {
			Result hookResult = checker.check(atom, permissionLevel);
			if (hookResult.isError()) {
				throw new RadixEngineException(
					atom,
					RadixEngineErrorCode.HOOK_ERROR,
					"Checker failed: " + hookResult.getErrorMessage(),
					DataPointer.ofAtom()
				);
			}
		}

		return downedParticles;
	}

	/**
	 * Atomically stores the given atom into the store with default permission level USER.
	 * If the atom has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param atom atom to store
	 * @throws RadixEngineException on state conflict, dependency issues or bad atom
	 */
	public void execute(T atom) throws RadixEngineException {
		execute(atom, PermissionLevel.USER);
	}

	/**
	 * Atomically stores the given atom into the store. If the atom
	 * has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param atom the atom to store
	 * @param permissionLevel permission level to execute on
	 * @throws RadixEngineException on state conflict or dependency issues
	 */
	public void execute(T atom, PermissionLevel permissionLevel) throws RadixEngineException {
		synchronized (stateUpdateEngineLock) {
			if (!branches.isEmpty()) {
				throw new IllegalStateException(
					String.format(
						"%s transient branches still exist. Must delete branches before storing additional atoms.",
						branches.size()
					)
				);
			}

			// TODO: combine verification and storage
			var downedParticles = this.verify(atom, permissionLevel);
			this.engineStore.storeAtom(atom);

			// TODO Feature: Return updated state for some given query (e.g. for current validator set)
			// Non-persisted computed state
			final var cmInstruction = atom.getCMInstruction();
			for (CMMicroInstruction microInstruction : cmInstruction.getMicroInstructions()) {
				// Treat check spin as the first push for now
				if (!microInstruction.isCheckSpin()) {
					continue;
				}

				final Particle particle;
				if (microInstruction.getParticle() == null) {
					particle = downedParticles.get(microInstruction.getParticleHash());
					if (particle == null) {
						throw new IllegalStateException();
					}
				} else {
					particle = microInstruction.getParticle();
				}

				stateComputers.forEach((a, computer) -> computer.processCheckSpin(particle, microInstruction.getCheckSpin()));
			}
		}
	}

	public boolean contains(T atom) {
		return engineStore.containsAtom(atom);
	}
}
