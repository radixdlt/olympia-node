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

import com.google.common.collect.Iterables;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;

import com.radixdlt.store.SpinStateMachine;
import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<M> {
	private static final Logger logger = LogManager.getLogger();

	private static class ApplicationStateComputer<U, V extends Particle, M> {
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

		ApplicationStateComputer<U, V, M> copy() {
			return new ApplicationStateComputer<>(
				particleClass,
				curValue,
				outputReducer,
				inputReducer,
				includeInBranches
			);
		}

		void initialize(EngineStore<M> engineStore) {
			curValue = engineStore.reduceUpParticles(particleClass, curValue, outputReducer);
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

	private static final class SubstateCache<T extends Particle> {
		private final Predicate<T> particleCheck;
		private final HashMap<SubstateId, Substate> cache = new HashMap<>();
		private final boolean includeInBranches;

		SubstateCache(Predicate<T> particleCheck, boolean includeInBranches) {
			this.particleCheck = particleCheck;
			this.includeInBranches = includeInBranches;
		}

		public SubstateCache<T> copy() {
			var copy = new SubstateCache<>(particleCheck, includeInBranches);
			copy.cache.putAll(cache);
			return copy;
		}

		public SubstateCache<T> bringUp(Substate upSubstate) {
			if (particleCheck.test((T) upSubstate.getParticle())) {
				this.cache.put(upSubstate.getId(), upSubstate);
			}
			return this;
		}

		public SubstateCache<T> shutDown(SubstateId substateId) {
			this.cache.remove(substateId);
			return this;
		}
	}

	private final ConstraintMachine constraintMachine;
	private final EngineStore<M> engineStore;
	private final PostParsedChecker checker;
	private final Object stateUpdateEngineLock = new Object();
	private final Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> stateComputers = new HashMap<>();
	private final Map<Class<?>, SubstateCache<?>> substateCache = new HashMap<>();
	private final List<RadixEngineBranch<M>> branches = new ArrayList<>();
	private final BatchVerifier<M> batchVerifier;

	public RadixEngine(
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore
	) {
		this(constraintMachine, engineStore, null, BatchVerifier.empty());
	}

	public RadixEngine(
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore,
		PostParsedChecker checker,
		BatchVerifier<M> batchVerifier
	) {
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.engineStore = Objects.requireNonNull(engineStore);
		this.checker = checker;
		this.batchVerifier = batchVerifier;
	}

	public <T extends Particle> void addSubstateCache(SubstateCacheRegister<T> substateCacheRegister, boolean includeInBranches) {
		synchronized (stateUpdateEngineLock) {
			if (substateCache.containsKey(substateCacheRegister.getParticleClass())) {
				throw new IllegalStateException();
			}

			var cache = new SubstateCache<>(substateCacheRegister.getParticlePredicate(), includeInBranches);
			var substateIterable = engineStore.upSubstates(
				substateCacheRegister.getParticleClass(),
				substateCacheRegister.getParticlePredicate()
			);
			substateIterable.forEach(cache::bringUp);
			substateCache.put(substateCacheRegister.getParticleClass(), cache);
		}
	}

	public <T> T getSubstateCache(
		List<Class<? extends Particle>> particleClasses,
		Function<Iterable<Substate>, T> func
	) {
		synchronized (stateUpdateEngineLock) {
			var substates = Iterables.concat(
				particleClasses.stream()
					.map(substateCache::get)
					.map(s -> s.cache.values())
					.collect(Collectors.toList())
			);

			return func.apply(substates);
		}
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
		ApplicationStateComputer<U, V, M> applicationStateComputer = new ApplicationStateComputer<>(
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
	 */
	public static class RadixEngineBranch<M> {
		private final RadixEngine<M> engine;

		private RadixEngineBranch(
			ConstraintMachine constraintMachine,
			EngineStore<M> parentStore,
			PostParsedChecker checker,
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> stateComputers,
			Map<Class<?>, SubstateCache<?>> substateCache
		) {
			var transientEngineStore = new TransientEngineStore<>(parentStore);

			this.engine = new RadixEngine<>(
				constraintMachine,
				transientEngineStore,
				checker,
				BatchVerifier.empty()
			);

			engine.substateCache.putAll(substateCache);
			engine.stateComputers.putAll(stateComputers);
		}

		public List<ParsedTransaction> execute(List<Atom> atoms) throws RadixEngineException {
			return engine.execute(atoms);
		}

		public List<ParsedTransaction> execute(List<Atom> atoms, PermissionLevel permissionLevel) throws RadixEngineException {
			return engine.execute(atoms, null, permissionLevel);
		}

		public <T> T getSubstateCache(
			List<Class<? extends Particle>> particleClasses,
			Function<Iterable<Substate>, T> func
		) {
			return engine.getSubstateCache(particleClasses, func);
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

	public RadixEngineBranch<M> transientBranch() {
		synchronized (stateUpdateEngineLock) {
			Map<Pair<Class<?>, String>, ApplicationStateComputer<?, ?, M>> branchedStateComputers = new HashMap<>();
			this.stateComputers.forEach((c, computer) -> {
				if (computer.includeInBranches) {
					branchedStateComputers.put(c, computer.copy());
				}
			});
			var branchedCache = new HashMap<Class<?>, SubstateCache<?>>();
			this.substateCache.forEach((c, cache) -> {
				if (cache.includeInBranches) {
					branchedCache.put(c, cache.copy());
				}
			});
			RadixEngineBranch<M> branch = new RadixEngineBranch<>(
				this.constraintMachine,
				this.engineStore,
				this.checker,
				branchedStateComputers,
				branchedCache
			);

			branches.add(branch);

			return branch;
		}
	}


	private ParsedTransaction verify(CMStore.Transaction txn, Atom atom, PermissionLevel permissionLevel)
		throws RadixEngineException {
		var parsedInstructions = new ArrayList<ParsedInstruction>();
		final Optional<CMError> error = constraintMachine.validate(
			txn,
			engineStore,
			atom,
			permissionLevel,
			parsedInstructions
		);

		if (error.isPresent()) {
			CMError e = error.get();
			throw new RadixEngineException(atom, RadixEngineErrorCode.CM_ERROR, e.getErrorDescription(), e.getDataPointer(), e);
		}

		var parsedTransaction = new ParsedTransaction(atom, parsedInstructions);

		if (checker != null) {
			Result hookResult = checker.check(atom, permissionLevel, parsedTransaction);
			if (hookResult.isError()) {
				throw new RadixEngineException(
					atom,
					RadixEngineErrorCode.HOOK_ERROR,
					"Checker failed: " + hookResult.getErrorMessage(),
					DataPointer.ofAtom()
				);
			}
		}

		return parsedTransaction;
	}

	/**
	 * Atomically stores the given atom into the store with default permission level USER.
	 * If the atom has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param atoms atom to store
	 * @throws RadixEngineException on state conflict, dependency issues or bad atom
	 */
	public List<ParsedTransaction> execute(List<Atom> atoms) throws RadixEngineException {
		return execute(atoms, null, PermissionLevel.USER);
	}

	/**
	 * Atomically stores the given atom into the store. If the atom
	 * has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param atoms atoms to store
	 * @param permissionLevel permission level to execute on
	 * @throws RadixEngineException on state conflict or dependency issues
	 */
	public List<ParsedTransaction> execute(List<Atom> atoms, M meta, PermissionLevel permissionLevel) throws RadixEngineException {
		synchronized (stateUpdateEngineLock) {
			if (!branches.isEmpty()) {
				throw new IllegalStateException(
					String.format(
						"%s transient branches still exist. Must delete branches before storing additional atoms.",
						branches.size()
					)
				);
			}
			var txn = engineStore.createTransaction();
			try {
				var parsedTransactions = executeInternal(txn, atoms, meta, permissionLevel);
				txn.commit();
				return parsedTransactions;
			} catch (Exception e) {
				txn.abort();
				throw e;
			}
		}
	}

	private List<ParsedTransaction> executeInternal(
		CMStore.Transaction txn,
		List<Atom> atoms,
		M meta,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var checker = batchVerifier.newVerifier(this::getComputedState);
		var parsedTransactions = new ArrayList<ParsedTransaction>();
		for (var atom : atoms) {
			// TODO: combine verification and storage
			var parsedTransaction = this.verify(txn, atom, permissionLevel);
			try {
				this.engineStore.storeAtom(txn, atom);
			} catch (Exception e) {
				logger.error("Store of atom {} failed. parsed: {}", atom, parsedTransaction);
				throw e;
			}

			// TODO Feature: Return updated state for some given query (e.g. for current validator set)
			// Non-persisted computed state
			for (ParsedInstruction parsedInstruction : parsedTransaction.instructions()) {
				final var particle = parsedInstruction.getSubstate().getParticle();
				final var checkSpin = SpinStateMachine.prev(parsedInstruction.getSpin());
				stateComputers.forEach((a, computer) -> computer.processCheckSpin(particle, checkSpin));

				var cache = substateCache.get(particle.getClass());
				if (cache != null) {
					if (parsedInstruction.getSpin() == Spin.UP) {
						cache.bringUp(parsedInstruction.getSubstate());
					} else {
						cache.shutDown(parsedInstruction.getSubstate().getId());
					}
				}

				if (parsedInstruction.getSpin() == Spin.UP) {
					checker.test(this::getComputedState);
				}
			}
			parsedTransactions.add(parsedTransaction);
		}

		checker.testMetadata(meta, this::getComputedState);
		if (meta != null) {
			this.engineStore.storeMetadata(txn, meta);
		}

		return parsedTransactions;
	}
}
