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
import com.radixdlt.application.system.construction.FeeReserveCompleteException;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.application.tokens.Amount;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.ShutdownAllIndex;
import com.radixdlt.constraintmachine.SubstateDeserialization;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.REStateUpdate;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.exceptions.TxnParseException;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.EngineStore;

import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
		private final REParser reParser;
		private final BiFunction<U, Particle, U> outputReducer;
		private final BiFunction<U, Particle, U> inputReducer;
		private final boolean includeInBranches;
		private U curValue;

		ApplicationStateReducer(
			Set<Class<? extends Particle>> particleClasses,
			U initialValue,
			BiFunction<U, Particle, U> outputReducer,
			BiFunction<U, Particle, U> inputReducer,
			boolean includeInBranches,
			REParser reParser
		) {
			this.particleClasses = particleClasses;
			this.reParser = reParser;
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
				includeInBranches,
				reParser
			);
		}

		void initialize(EngineStore<M> engineStore) {
			for (var particleClass : particleClasses) {
				curValue = engineStore.reduceUpParticles(curValue, outputReducer, reParser.getSubstateDeserialization(), particleClass);
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
	private SubstateSerialization serialization;
	private BatchVerifier<M> batchVerifier;
	private REConstructor actionConstructors;
	private ConstraintMachine constraintMachine;

	public RadixEngine(
		REParser parser,
		SubstateSerialization serialization,
		REConstructor actionConstructors,
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore
	) {
		this(parser, serialization, actionConstructors, constraintMachine, engineStore, BatchVerifier.empty());
	}

	public RadixEngine(
		REParser parser,
		SubstateSerialization serialization,
		REConstructor actionConstructors,
		ConstraintMachine constraintMachine,
		EngineStore<M> engineStore,
		BatchVerifier<M> batchVerifier
	) {
		this.parser = Objects.requireNonNull(parser);
		this.serialization = Objects.requireNonNull(serialization);
		this.actionConstructors = Objects.requireNonNull(actionConstructors);
		this.constraintMachine = Objects.requireNonNull(constraintMachine);
		this.engineStore = Objects.requireNonNull(engineStore);
		this.batchVerifier = batchVerifier;
	}

	/*
	public <T extends Particle> void addSubstateCache(SubstateCacheRegister<T> substateCacheRegister, boolean includeInBranches) {
		synchronized (stateUpdateEngineLock) {
			if (substateCache.containsKey(substateCacheRegister.getParticleClass())) {
				throw new IllegalStateException("Already added " + substateCacheRegister.getParticleClass());
			}

			var cache = new SubstateCache<>(substateCacheRegister.getParticlePredicate(), includeInBranches);
			try (var cursor = engineStore.openIndexedCursor(
				substateCacheRegister.getParticleClass(),
				parser.getSubstateDeserialization()
			)) {
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
	 */


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
			includeInBranches,
			parser
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
		SubstateSerialization serialization,
		REConstructor actionToConstructorMap,
		BatchVerifier<M> batchVerifier,
		REParser parser
	) {
		synchronized (stateUpdateEngineLock) {
			this.constraintMachine = new ConstraintMachine(
				constraintMachineConfig.getVirtualStoreLayer(),
				constraintMachineConfig.getProcedures(),
				constraintMachineConfig.getMeter()
			);
			this.actionConstructors = actionToConstructorMap;
			this.batchVerifier = batchVerifier;
			this.parser = parser;
			this.serialization = serialization;
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
			SubstateSerialization serialization,
			REConstructor actionToConstructorMap,
			ConstraintMachine constraintMachine,
			EngineStore<M> parentStore,
			Map<Pair<Class<?>, String>, ApplicationStateReducer<?, M>> stateComputers,
			Map<Class<?>, SubstateCache<?>> substateCache
		) {
			var transientEngineStore = new TransientEngineStore<>(parentStore);

			this.engine = new RadixEngine<>(
				parser,
				serialization,
				actionToConstructorMap,
				constraintMachine,
				transientEngineStore,
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
				this.serialization,
				this.actionConstructors,
				this.constraintMachine,
				this.engineStore,
				branchedStateComputers,
				branchedCache
			);

			branches.add(branch);

			return branch;
		}
	}

	private REProcessedTxn verify(EngineStore.Transaction dbTransaction, Txn txn, ExecutionContext context)
		throws AuthorizationException, TxnParseException, ConstraintMachineException {

		var parsedTxn = parser.parse(txn);
		parsedTxn.getSignedBy().ifPresent(context::setKey);
		context.setDisableResourceAllocAndDestroy(parsedTxn.disableResourceAllocAndDestroy());

		var stateUpdates = constraintMachine.verify(
			parser.getSubstateDeserialization(),
			new CMStore() {
				@Override
				public boolean isVirtualDown(SubstateId substateId) {
					return engineStore.isVirtualDown(dbTransaction, substateId);
				}

				@Override
				public Optional<Particle> loadUpParticle(SubstateId substateId, SubstateDeserialization deserialization) {
					return engineStore.loadUpParticle(dbTransaction, substateId, deserialization);
				}

				@Override
				public CloseableCursor<RawSubstateBytes> openIndexedCursor(ShutdownAllIndex index) {
					return engineStore.openIndexedCursor(dbTransaction, index);
				}

				@Override
				public Optional<Particle> loadAddr(REAddr addr, SubstateDeserialization deserialization) {
					return engineStore.loadAddr(dbTransaction, addr, deserialization);
				}
			},
			context,
			parsedTxn.instructions()
		);

		return new REProcessedTxn(parsedTxn, stateUpdates, context.getEvents());
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
		EngineStore.Transaction dbTransaction,
		List<Txn> txns,
		M meta,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var checker = batchVerifier.newVerifier(this::getComputedState);
		var parsedTransactions = new ArrayList<REProcessedTxn>();

		// FIXME: This is quite the hack to increase sigsLeft for execution on noncommits (e.g. mempool)
		// FIXME: Should probably just change metering
		var sigsLeft = meta != null ? 0 : 1000; // Start with 0

		for (int i = 0; i < txns.size(); i++) {
			var txn = txns.get(i);
			var context = new ExecutionContext(txn, permissionLevel, sigsLeft, Amount.ofTokens(200).toSubunits());

			final REProcessedTxn parsedTxn;
			// TODO: combine verification and storage
			try {
				parsedTxn = this.verify(dbTransaction, txn, context);
			} catch (TxnParseException | AuthorizationException | ConstraintMachineException e) {
				throw new RadixEngineException(i, txns.size(), txn, e);
			}
			// Carry sigs left to the next transaction
			sigsLeft = context.sigsLeft();

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
		return construct(executable, Set.of());
	}

	private TxBuilder construct(TxBuilderExecutable executable, Set<SubstateId> avoid) throws TxBuilderException {
		synchronized (stateUpdateEngineLock) {
			SubstateStore substateStore = engineStore;/*(c, d) -> {
				var cache = substateCache.get(c);
				if (cache == null) {
					return engineStore.openIndexedCursor(c, d);
				}

				var cacheIterator = cache.cache.asMap().values().iterator();

				return CloseableCursor.concat(
					CloseableCursor.wrapIterator(cacheIterator),
					() -> CloseableCursor.filter(
						engineStore.openIndexedCursor(c, parser.getSubstateDeserialization()),
						next -> !cache.cache.asMap().containsKey(next.getId())
					)
				);
			};*/

			SubstateStore filteredStore = b -> CloseableCursor.filter(
				substateStore.openIndexedCursor(b),
				i -> !avoid.contains(SubstateId.fromBytes(i.getId()))
			);

			var txBuilder = TxBuilder.newBuilder(
				filteredStore,
				parser.getSubstateDeserialization(),
				serialization
			);

			executable.execute(txBuilder);

			return txBuilder;
		}
	}

	public TxBuilder construct(TxAction action) throws TxBuilderException {
		return construct(TxnConstructionRequest.create().action(action));
	}

	public TxBuilder construct(TxnConstructionRequest request) throws TxBuilderException {
		var feePayer = request.getFeePayer();
		if (feePayer.isPresent()) {
			return constructWithFees(request, feePayer.get());
		} else {
			return construct(
				txBuilder -> {
					if (request.isDisableResourceAllocAndDestroy()) {
						txBuilder.toLowLevelBuilder().disableResourceAllocAndDestroy();
					}
					for (var action : request.getActions()) {
						this.actionConstructors.construct(action, txBuilder);
					}
					request.getMsg().ifPresent(txBuilder::message);
				},
				request.getSubstatesToAvoid()
			);
		}
	}

	private TxBuilder constructWithFees(TxnConstructionRequest request, REAddr feePayer) throws TxBuilderException {
		int maxTries = 5;
		var perByteFee = this.actionConstructors.getPerByteFee().orElse(UInt256.ZERO);
		var feeGuess = new AtomicReference<>(perByteFee.multiply(UInt256.from(100))); // Close to minimum size
		for (int i = 0; i < maxTries; i++) {
			try {
				return construct(
					txBuilder -> {
						if (request.isDisableResourceAllocAndDestroy()) {
							txBuilder.toLowLevelBuilder().disableResourceAllocAndDestroy();
						}

						this.actionConstructors.construct(new FeeReservePut(feePayer, feeGuess.get()), txBuilder);
						for (var action : request.getActions()) {
							this.actionConstructors.construct(action, txBuilder);
						}
						this.actionConstructors.construct(new FeeReserveComplete(feePayer), txBuilder);

						request.getMsg().ifPresent(txBuilder::message);
					},
					request.getSubstatesToAvoid()
				);
			} catch (FeeReserveCompleteException e) {
				feeGuess.set(e.getExpectedFee());
			}
		}

		throw new TxBuilderException("Not enough fees: unable to construct with fees after " + maxTries + " tries.");
	}
}
