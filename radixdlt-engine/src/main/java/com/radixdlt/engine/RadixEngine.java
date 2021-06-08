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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateCursor;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.ConstraintMachineException;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.TxnParseException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;

import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<M> {
	private static final Logger logger = LogManager.getLogger();

	private static class ApplicationStateReducer<U, M> {
		private final Set<Class<? extends Particle>> particleClasses;
		private final BiFunction<U, Particle, U> outputReducer;
		private final BiFunction<U, Particle, U> inputReducer;
		private final boolean includeInBranches;
		private U curValue;

		ApplicationStateReducer(
			Set<Class<? extends Particle>> particleClasses,
			U initialValue,
			BiFunction<U, Particle, U> outputReducer,
			BiFunction<U, Particle, U> inputReducer,
			boolean includeInBranches
		) {
			this.particleClasses = particleClasses;
			this.curValue = initialValue;
			this.outputReducer = outputReducer;
			this.inputReducer = inputReducer;
			this.includeInBranches = includeInBranches;
		}

		ApplicationStateReducer<U, M> copy() {
			return new ApplicationStateReducer<>(
				particleClasses,
				curValue,
				outputReducer,
				inputReducer,
				includeInBranches
			);
		}

		void initialize(EngineStore<M> engineStore) {
			for (var particleClass : particleClasses) {
				curValue = engineStore.reduceUpParticles(particleClass, curValue, outputReducer);
			}
		}

		void processStateUpdate(REStateUpdate stateUpdate) {
			for (var particleClass : particleClasses) {
				var p = stateUpdate.getSubstate().getParticle();
				if (particleClass.isInstance(p)) {
					if (stateUpdate.isBootUp()) {
						curValue = outputReducer.apply(curValue, p);
					} else {
						curValue = inputReducer.apply(curValue, p);
					}
				}
			}
		}
	}

	private static final class SubstateCache<T extends Particle> {
		private final Predicate<T> particleCheck;
		private final Cache<SubstateId, Substate> cache = CacheBuilder.newBuilder()
			.maximumSize(1000)
			.build();

		private final boolean includeInBranches;

		SubstateCache(Predicate<T> particleCheck, boolean includeInBranches) {
			this.particleCheck = particleCheck;
			this.includeInBranches = includeInBranches;
		}

		public SubstateCache<T> copy() {
			var copy = new SubstateCache<>(particleCheck, includeInBranches);
			copy.cache.putAll(cache.asMap());
			return copy;
		}

		public boolean test(Particle particle) {
			return particleCheck.test((T) particle);
		}

		public SubstateCache<T> bringUp(Substate upSubstate) {
			if (particleCheck.test((T) upSubstate.getParticle())) {
				this.cache.put(upSubstate.getId(), upSubstate);
			}
			return this;
		}

		public SubstateCache<T> shutDown(SubstateId substateId) {
			this.cache.invalidate(substateId);
			return this;
		}
	}

	private final EngineStore<M> engineStore;
	private final Object stateUpdateEngineLock = new Object();
	private final Map<Pair<Class<?>, String>, ApplicationStateReducer<?, M>> stateComputers = new HashMap<>();
	private final Map<Class<?>, SubstateCache<?>> substateCache = new HashMap<>();
	private final List<RadixEngineBranch<M>> branches = new ArrayList<>();

	private REParser parser;
	private BatchVerifier<M> batchVerifier;
	private ActionConstructors actionConstructors;
	private ConstraintMachine constraintMachine;
	private PostProcessedVerifier postProcessedVerifier;

	public RadixEngine(
		REParser parser,
		ActionConstructors actionConstructors,
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore
	) {
		this(parser, actionConstructors, constraintMachine, engineStore, null, BatchVerifier.empty());
	}

	public RadixEngine(
		REParser parser,
		ActionConstructors actionConstructors,
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore,
		PostProcessedVerifier postProcessedVerifier,
		BatchVerifier<M> batchVerifier
	) {
		this.parser = Objects.requireNonNull(parser);
		this.actionConstructors = Objects.requireNonNull(actionConstructors);
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.engineStore = Objects.requireNonNull(engineStore);
		this.postProcessedVerifier = postProcessedVerifier;
		this.batchVerifier = batchVerifier;
	}

	public <T extends Particle> void addSubstateCache(SubstateCacheRegister<T> substateCacheRegister, boolean includeInBranches) {
		synchronized (stateUpdateEngineLock) {
			if (substateCache.containsKey(substateCacheRegister.getParticleClass())) {
				throw new IllegalStateException("Already added " + substateCacheRegister.getParticleClass());
			}

			var cache = new SubstateCache<>(substateCacheRegister.getParticlePredicate(), includeInBranches);
			try (var cursor = engineStore.openIndexedCursor(substateCacheRegister.getParticleClass())) {
				cursor.forEachRemaining(substate -> {
					var p = substateCacheRegister.getParticleClass().cast(substate.getParticle());
					if (substateCacheRegister.getParticlePredicate().test(p)) {
						cache.bringUp(substate);
					}
				});
			}
			substateCache.put(substateCacheRegister.getParticleClass(), cache);
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
	 */
	public <U> void addStateReducer(StateReducer<U> stateReducer, String name, boolean includeInBranches) {
		ApplicationStateReducer<U, M> applicationStateComputer = new ApplicationStateReducer<>(
			stateReducer.particleClasses(),
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

	public <U> void addStateReducer(StateReducer<U> stateReducer, boolean includeInBranches) {
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

	public void replaceConstraintMachine(
		ConstraintMachineConfig constraintMachineConfig,
		ActionConstructors actionToConstructorMap,
		BatchVerifier<M> batchVerifier,
		REParser parser,
		PostProcessedVerifier postProcessedVerifier
	) {
		synchronized (stateUpdateEngineLock) {
			this.constraintMachine = new ConstraintMachine(
				constraintMachineConfig.getVirtualStoreLayer(),
				constraintMachineConfig.getProcedures(),
				constraintMachineConfig.getMetering()
			);
			this.actionConstructors = actionToConstructorMap;
			this.batchVerifier = batchVerifier;
			this.parser = parser;
			this.postProcessedVerifier = postProcessedVerifier;
		}
	}


	/**
	 * A cheap radix engine branch which is purely transient
	 */
	public static class RadixEngineBranch<M> {
		private final RadixEngine<M> engine;
		private boolean deleted = false;

		private RadixEngineBranch(
			REParser parser,
			ActionConstructors actionToConstructorMap,
			ConstraintMachine constraintMachine,
			EngineStore<M> parentStore,
			PostProcessedVerifier checker,
			Map<Pair<Class<?>, String>, ApplicationStateReducer<?, M>> stateComputers,
			Map<Class<?>, SubstateCache<?>> substateCache
		) {
			var transientEngineStore = new TransientEngineStore<>(parentStore);

			this.engine = new RadixEngine<>(
				parser,
				actionToConstructorMap,
				constraintMachine,
				transientEngineStore,
				checker,
				BatchVerifier.empty()
			);

			engine.substateCache.putAll(substateCache);
			engine.stateComputers.putAll(stateComputers);
		}

		private void delete() {
			deleted = true;
		}

		private void assertNotDeleted() {
			if (deleted) {
				throw new IllegalStateException();
			}
		}

		public List<REProcessedTxn> execute(List<Txn> txns) throws RadixEngineException {
			assertNotDeleted();
			return engine.execute(txns);
		}

		public List<REProcessedTxn> execute(List<Txn> txns, PermissionLevel permissionLevel) throws RadixEngineException {
			assertNotDeleted();
			return engine.execute(txns, null, permissionLevel);
		}

		public TxBuilder construct(TxAction action) throws TxBuilderException {
			assertNotDeleted();
			return engine.construct(action);
		}

		public TxBuilder construct(TxnConstructionRequest request) throws TxBuilderException {
			assertNotDeleted();
			return engine.construct(request);
		}

		public <U> U getComputedState(Class<U> applicationStateClass) {
			assertNotDeleted();
			return engine.getComputedState(applicationStateClass);
		}
	}

	public void deleteBranches() {
		synchronized (stateUpdateEngineLock) {
			branches.forEach(RadixEngineBranch::delete);
			branches.clear();
		}
	}

	public RadixEngineBranch<M> transientBranch() {
		synchronized (stateUpdateEngineLock) {
			Map<Pair<Class<?>, String>, ApplicationStateReducer<?, M>> branchedStateComputers = new HashMap<>();
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
				this.parser,
				this.actionConstructors,
				this.constraintMachine,
				this.engineStore,
				this.postProcessedVerifier,
				branchedStateComputers,
				branchedCache
			);

			branches.add(branch);

			return branch;
		}
	}

	private REProcessedTxn verify(CMStore.Transaction dbTransaction, Txn txn, PermissionLevel permissionLevel)
		throws TxnParseException, ConstraintMachineException {

		var parsedTxn = parser.parse(txn);
		var stateUpdates = constraintMachine.verify(
			dbTransaction,
			engineStore,
			permissionLevel,
			parsedTxn.instructions(),
			parsedTxn.getSignedBy(),
			parsedTxn.disableResourceAllocAndDestroy()
		);
		var processedTxn = new REProcessedTxn(parsedTxn, stateUpdates);

		if (postProcessedVerifier != null) {
			postProcessedVerifier.check(permissionLevel, processedTxn);
		}

		return processedTxn;
	}

	/**
	 * Atomically stores the given atom into the store with default permission level USER.
	 * If the atom has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @throws RadixEngineException on state conflict, dependency issues or bad atom
	 */
	public List<REProcessedTxn> execute(List<Txn> txns) throws RadixEngineException {
		return execute(txns, null, PermissionLevel.USER);
	}

	/**
	 * Atomically stores the given atom into the store. If the atom
	 * has any conflicts or dependency issues the atom will not be stored.
	 *
	 * @param txns transactions to execute
	 * @param permissionLevel permission level to execute on
	 * @throws RadixEngineException on state conflict or dependency issues
	 */
	public List<REProcessedTxn> execute(List<Txn> txns, M meta, PermissionLevel permissionLevel) throws RadixEngineException {
		synchronized (stateUpdateEngineLock) {
			if (!branches.isEmpty()) {
				throw new IllegalStateException(
					String.format(
						"%s transient branches still exist. Must delete branches before storing additional atoms.",
						branches.size()
					)
				);
			}
			var dbTransaction = engineStore.createTransaction();
			try {
				var parsedTransactions = executeInternal(dbTransaction, txns, meta, permissionLevel);
				dbTransaction.commit();
				return parsedTransactions;
			} catch (Exception e) {
				dbTransaction.abort();
				throw e;
			}
		}
	}

	private List<REProcessedTxn> executeInternal(
		CMStore.Transaction dbTransaction,
		List<Txn> txns,
		M meta,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var checker = batchVerifier.newVerifier(this::getComputedState);
		var parsedTransactions = new ArrayList<REProcessedTxn>();
		for (var txn : txns) {
			final REProcessedTxn parsedTxn;
			// TODO: combine verification and storage
			try {
				parsedTxn = this.verify(dbTransaction, txn, permissionLevel);
			} catch (TxnParseException | ConstraintMachineException e) {
				throw new RadixEngineException(txn, e);
			}

			try {
				this.engineStore.storeTxn(dbTransaction, txn, parsedTxn.stateUpdates().collect(Collectors.toList()));
			} catch (Exception e) {
				logger.error("Store of atom failed: " + parsedTxn, e);
				throw e;
			}

			// TODO Feature: Return updated state for some given query (e.g. for current validator set)
			// Non-persisted computed state
			for (var group : parsedTxn.getGroupedStateUpdates()) {
				group.forEach(update -> {
					stateComputers.forEach((a, computer) -> computer.processStateUpdate(update));
					final var particle = update.getSubstate().getParticle();
					var cache = substateCache.get(particle.getClass());
					if (cache != null && cache.test(particle)) {
						if (update.isBootUp()) {
							cache.bringUp(update.getSubstate());
						} else {
							cache.shutDown(update.getSubstate().getId());
						}
					}
				});

				checker.test(this::getComputedState);
			}

			parsedTransactions.add(parsedTxn);
		}

		try {
			checker.testMetadata(meta, this::getComputedState);
		} catch (MetadataException e) {
			logger.error("Invalid metadata: " + parsedTransactions);
			throw e;
		}

		if (meta != null) {
			this.engineStore.storeMetadata(dbTransaction, meta);
		}

		return parsedTransactions;
	}

	public interface TxBuilderExecutable {
		void execute(TxBuilder txBuilder) throws TxBuilderException;
	}

	public TxBuilder construct(TxBuilderExecutable executable) throws TxBuilderException {
		return construct(null, executable, Set.of());
	}

	public TxBuilder construct(ECPublicKey user, TxBuilderExecutable executable, Set<SubstateId> avoid) throws TxBuilderException {
		synchronized (stateUpdateEngineLock) {
			SubstateStore substateStore = c -> {
				var cache = substateCache.get(c);
				if (cache == null) {
					return engineStore.openIndexedCursor(c);
				}

				var cacheIterator = cache.cache.asMap().values().iterator();

				return SubstateCursor.concat(
					SubstateCursor.wrapIterator(cacheIterator),
					() -> SubstateCursor.filter(
						engineStore.openIndexedCursor(c),
						next -> !cache.cache.asMap().containsKey(next.getId())
					)
				);
			};

			SubstateStore filteredStore = c -> SubstateCursor.filter(
				substateStore.openIndexedCursor(c),
				i -> !avoid.contains(i.getId())
			);

			var txBuilder = user != null
				? TxBuilder.newBuilder(user, filteredStore)
				: TxBuilder.newBuilder(filteredStore);

			executable.execute(txBuilder);

			return txBuilder;
		}
	}

	public TxBuilder construct(TxAction action) throws TxBuilderException {
		return construct(null, List.of(action));
	}

	public TxBuilder construct(ECPublicKey user, TxAction action) throws TxBuilderException {
		return construct(user, List.of(action));
	}

	public TxBuilder construct(ECPublicKey user, List<TxAction> actions) throws TxBuilderException {
		return construct(user, actions, Set.of());
	}

	public TxBuilder construct(ECPublicKey user, List<TxAction> actions, Set<SubstateId> avoid) throws TxBuilderException {
		return construct(
			user,
			txBuilder -> {
				for (var action : actions) {
					this.actionConstructors.construct(action, txBuilder);
					txBuilder.end();
				}
			},
			avoid
		);
	}

	public TxBuilder construct(TxnConstructionRequest request) throws TxBuilderException {
		return construct(
			null,
			txBuilder -> {
				if (request.isDisableResourceAllocAndDestroy()) {
					txBuilder.toLowLevelBuilder().disableResourceAllocAndDestroy();
				}
				for (var action : request.getActions()) {
					this.actionConstructors.construct(action, txBuilder);
					txBuilder.end();
				}
				request.getMsg().ifPresent(txBuilder::message);
			},
			Set.of()
		);
	}
}
