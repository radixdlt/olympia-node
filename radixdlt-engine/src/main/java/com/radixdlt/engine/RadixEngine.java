/* Copyright 2021 Radix DLT Ltd incorporated in England.
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.engine;

import com.google.common.base.Stopwatch;
import com.radixdlt.application.system.construction.FeeReserveCompleteException;
import com.radixdlt.application.tokens.ResourceInBucket;
import com.radixdlt.atom.CloseableCursor;
import com.radixdlt.atom.REConstructor;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.Txn;
import com.radixdlt.atom.TxnConstructionRequest;
import com.radixdlt.atom.actions.FeeReserveComplete;
import com.radixdlt.atom.actions.FeeReservePut;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.ExecutionContext;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.PermissionLevel;
import com.radixdlt.constraintmachine.REProcessedTxn;
import com.radixdlt.constraintmachine.RawSubstateBytes;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.constraintmachine.SystemMapKey;
import com.radixdlt.constraintmachine.exceptions.AuthorizationException;
import com.radixdlt.constraintmachine.exceptions.ConstraintMachineException;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.engine.parser.exceptions.TxnParseException;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.store.EngineStore;
import com.radixdlt.store.TransientEngineStore;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Top Level Class for the Radix Engine, a real-time, shardable, distributed state machine.
 */
public final class RadixEngine<M> {
	private static final Logger logger = LogManager.getLogger();
	private final EngineStore<M> engineStore;
	private final Object stateUpdateEngineLock = new Object();
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

	public void replaceConstraintMachine(
		ConstraintMachineConfig constraintMachineConfig,
		SubstateSerialization serialization,
		REConstructor actionToConstructorMap,
		BatchVerifier<M> batchVerifier,
		REParser parser
	) {
		synchronized (stateUpdateEngineLock) {
			this.constraintMachine = new ConstraintMachine(
				constraintMachineConfig.getProcedures(),
				constraintMachineConfig.getDeserialization(),
				constraintMachineConfig.getVirtualSubstateDeserialization(),
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
			EngineStore<M> parentStore
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
		}

		private void delete() {
			deleted = true;
		}

		private void assertNotDeleted() {
			if (deleted) {
				throw new IllegalStateException();
			}
		}

		public RadixEngineResult execute(List<Txn> txns) throws RadixEngineException {
			assertNotDeleted();
			return engine.execute(txns);
		}

		public RadixEngineResult execute(List<Txn> txns, PermissionLevel permissionLevel) throws RadixEngineException {
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
	}

	public void deleteBranches() {
		synchronized (stateUpdateEngineLock) {
			branches.forEach(RadixEngineBranch::delete);
			branches.clear();
		}
	}

	public RadixEngineBranch<M> transientBranch() {
		synchronized (stateUpdateEngineLock) {
			RadixEngineBranch<M> branch = new RadixEngineBranch<>(
				this.parser,
				this.serialization,
				this.actionConstructors,
				this.constraintMachine,
				this.engineStore
			);

			branches.add(branch);

			return branch;
		}
	}

	private REProcessedTxn verify(EngineStore.EngineStoreInTransaction<M> engineStoreInTransaction, Txn txn, ExecutionContext context)
		throws AuthorizationException, TxnParseException, ConstraintMachineException {

		var parsedTxn = parser.parse(txn);
		parsedTxn.getSignedBy().ifPresent(context::setKey);
		context.setDisableResourceAllocAndDestroy(parsedTxn.disableResourceAllocAndDestroy());

		var stateUpdates = constraintMachine.verify(
			engineStoreInTransaction,
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
	public RadixEngineResult execute(List<Txn> txns) throws RadixEngineException {
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
	public RadixEngineResult execute(List<Txn> txns, M meta, PermissionLevel permissionLevel) throws RadixEngineException {
		synchronized (stateUpdateEngineLock) {
			if (!branches.isEmpty()) {
				throw new IllegalStateException(
					String.format(
						"%s transient branches still exist. Must delete branches before storing additional atoms.",
						branches.size()
					)
				);
			}
			return engineStore.transaction(store -> executeInternal(store, txns, meta, permissionLevel));
		}
	}

	private RadixEngineResult executeInternal(
		EngineStore.EngineStoreInTransaction<M> engineStoreInTransaction,
		List<Txn> txns,
		M meta,
		PermissionLevel permissionLevel
	) throws RadixEngineException {
		var processedTxns = new ArrayList<REProcessedTxn>();

		// FIXME: This is quite the hack to increase sigsLeft for execution on noncommits (e.g. mempool)
		// FIXME: Should probably just change metering
		var sigsLeft = meta != null ? 0 : 1000; // Start with 0
		var storageStopwatch = Stopwatch.createUnstarted();
		var verificationStopwatch = Stopwatch.createUnstarted();

		for (int i = 0; i < txns.size(); i++) {
			var txn = txns.get(i);

			verificationStopwatch.start();
			var context = new ExecutionContext(txn, permissionLevel, sigsLeft);
			final REProcessedTxn processedTxn;
			try {
				processedTxn = this.verify(engineStoreInTransaction, txn, context);
			} catch (TxnParseException | AuthorizationException | ConstraintMachineException e) {
				throw new RadixEngineException(i, txns.size(), txn, e);
			}
			verificationStopwatch.stop();

			// Carry sigs left to the next transaction
			sigsLeft = context.sigsLeft();

			storageStopwatch.start();
			try {
				engineStoreInTransaction.storeTxn(processedTxn);
			} catch (Exception e) {
				logger.error("Store of atom failed: " + processedTxn, e);
				throw e;
			}
			storageStopwatch.stop();

			processedTxns.add(processedTxn);
		}

		try {
			batchVerifier.testMetadata(meta, processedTxns);
		} catch (MetadataException e) {
			logger.error("Invalid metadata: " + processedTxns);
			throw e;
		}

		if (meta != null) {
			engineStoreInTransaction.storeMetadata(meta);
		}

		return RadixEngineResult.create(
			processedTxns,
			verificationStopwatch.elapsed(TimeUnit.MILLISECONDS),
			storageStopwatch.elapsed(TimeUnit.MILLISECONDS)
		);
	}

	public interface TxBuilderExecutable {
		void execute(TxBuilder txBuilder) throws TxBuilderException;
	}

	public TxBuilder construct(TxBuilderExecutable executable) throws TxBuilderException {
		return construct(executable, Set.of());
	}

	private TxBuilder construct(TxBuilderExecutable executable, Set<SubstateId> avoid) throws TxBuilderException {
		synchronized (stateUpdateEngineLock) {
			SubstateStore filteredStore = new SubstateStore() {
				@Override
				public CloseableCursor<RawSubstateBytes> openIndexedCursor(SubstateIndex<?> index) {
					return engineStore.openIndexedCursor(index)
						.filter(i -> !avoid.contains(SubstateId.fromBytes(i.getId())));
				}

				@Override
				public Optional<RawSubstateBytes> get(SystemMapKey key) {
					return engineStore.get(key);
				}
			};

			var txBuilder = TxBuilder.newBuilder(
				filteredStore,
				constraintMachine.getDeserialization(),
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
						request.getMsg().ifPresent(txBuilder::message);
						this.actionConstructors.construct(new FeeReserveComplete(feePayer), txBuilder);
					},
					request.getSubstatesToAvoid()
				);
			} catch (FeeReserveCompleteException e) {
				feeGuess.set(e.getExpectedFee());
			}
		}

		throw new TxBuilderException("Not enough fees: unable to construct with fees after " + maxTries + " tries.");
	}

	public REParser getParser() {
		synchronized (stateUpdateEngineLock) {
			return parser;
		}
	}

	public Optional<Particle> get(SystemMapKey mapKey) {
		synchronized (stateUpdateEngineLock) {
			var deserialization = constraintMachine.getDeserialization();
			return engineStore.get(mapKey).map(raw -> {
				try {
					return deserialization.deserialize(raw.getData());
				} catch (DeserializeException e) {
					throw new IllegalStateException(e);
				}
			});
		}
	}

	public <K, T extends ResourceInBucket> Map<K, UInt384> reduceResources(
		Class<T> c,
		Function<T, K> keyMapper
	) {
		synchronized (stateUpdateEngineLock) {
			var deserialization = constraintMachine.getDeserialization();
			return reduce(deserialization.index(c), new HashMap<>(),
				(m, t) -> {
					m.merge(keyMapper.apply(t), UInt384.from(t.getAmount()), UInt384::add);
					return m;
				}
			);
		}
	}

	public <K, T extends ResourceInBucket> Map<K, UInt384> reduceResources(
		SubstateIndex<T> index,
		Function<T, K> keyMapper,
		Map<K, UInt384> initial
	) {
		return reduce(index, initial,
			(m, t) -> {
				m.merge(keyMapper.apply(t), UInt384.from(t.getAmount()), UInt384::add);
				return m;
			}
		);
	}

	public <K, T extends ResourceInBucket> Map<K, UInt384> reduceResources(
		SubstateIndex<T> index,
		Function<T, K> keyMapper
	) {
		return reduceResources(index, keyMapper, new HashMap<>());
	}

	public <K, T extends ResourceInBucket> Map<K, UInt384> reduceResources(
		SubstateIndex<T> index,
		Function<T, K> keyMapper,
		Predicate<T> predicate
	) {
		return reduce(index, new HashMap<>(),
			(m, t) -> {
				if (predicate.test(t)) {
					m.merge(keyMapper.apply(t), UInt384.from(t.getAmount()), UInt384::add);
				}
				return m;
			}
		);
	}

	public <K, T extends ResourceInBucket> Map<K, Pair<UInt384, Long>> reduceResourcesWithSubstateCount(
		SubstateIndex<T> index,
		Function<T, K> keyMapper,
		Predicate<T> predicate
	) {
		return reduce(index, new HashMap<>(),
			(m, t) -> {
				if (predicate.test(t)) {
					m.merge(
						keyMapper.apply(t),
						Pair.of(UInt384.from(t.getAmount()), 1L),
						(p0, p1) -> Pair.of(p0.getFirst().add(p1.getFirst()), p0.getSecond() + p1.getSecond())
					);
				}
				return m;
			}
		);
	}

	public <U, T extends Particle> U reduce(SubstateIndex<T> i, U identity, BiFunction<U, T, U> accumulator) {
		return reduce(i, identity, accumulator, Long.MAX_VALUE);
	}

	public <U, T extends Particle> U reduce(SubstateIndex<T> i, U identity, BiFunction<U, T, U> accumulator, long limit) {
		synchronized (stateUpdateEngineLock) {
			var deserialization = constraintMachine.getDeserialization();
			var u = identity;
			long count = 0;
			try (var cursor = engineStore.openIndexedCursor(i)) {
				while (cursor.hasNext() && count < limit) {
					try {
						var t = (T) deserialization.deserialize(cursor.next().getData());
						u = accumulator.apply(u, t);
						count++;
					} catch (DeserializeException e) {
						throw new IllegalStateException(e);
					}
				}
			}
			return u;
		}
	}

	public <U, T extends Particle> U reduce(Class<T> c, U identity, BiFunction<U, T, U> accumulator) {
		synchronized (stateUpdateEngineLock) {
			var deserialization = constraintMachine.getDeserialization();
			return reduce(deserialization.index(c), identity, accumulator);
		}
	}

	public <U, T extends Particle> U reduce(Class<T> c, U identity, BiFunction<U, T, U> accumulator, long limit) {
		synchronized (stateUpdateEngineLock) {
			var deserialization = constraintMachine.getDeserialization();
			return reduce(deserialization.index(c), identity, accumulator, limit);
		}
	}
}
