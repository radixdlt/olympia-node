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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.PostParsedChecker;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.engine.SubstateCacheRegister;
import com.radixdlt.store.EngineStore;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	@Override
	protected void configure() {
		bind(new TypeLiteral<BatchVerifier<LedgerAndBFTProof>>() { }).to(EpochProofVerifier.class).in(Scopes.SINGLETON);

		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<Pair<String, StateReducer<?, ?>>>() { });
		Multibinder.newSetBinder(binder(), PostParsedChecker.class);
		Multibinder.newSetBinder(binder(), new TypeLiteral<SubstateCacheRegister<?>>() { });
	}

	@Provides
	private ValidatorSetBuilder validatorSetBuilder(
		@MinValidators int minValidators,
		@MaxValidators int maxValidators
	) {
		return ValidatorSetBuilder.create(minValidators, maxValidators);
	}

	@Provides
	PostParsedChecker checker(Set<PostParsedChecker> checkers) {
		return (permissionLevel, reTxn) -> {
			for (var checker : checkers) {
				var result = checker.check(permissionLevel, reTxn);
				if (result.isError()) {
					return result;
				}
			}

			return Result.success();
		};
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAndBFTProof> getRadixEngine(
		ConstraintMachine constraintMachine,
		EngineStore<LedgerAndBFTProof> engineStore,
		PostParsedChecker checker,
		BatchVerifier<LedgerAndBFTProof> batchVerifier,
		Set<StateReducer<?, ?>> stateReducers,
		Set<Pair<String, StateReducer<?, ?>>> namedStateReducers,
		Set<SubstateCacheRegister<?>> substateCacheRegisters
	) {
		var radixEngine = new RadixEngine<>(
			constraintMachine,
			engineStore,
			checker,
			batchVerifier
		);

		// TODO: Convert to something more like the following:
		// RadixEngine
		//   .newStateComputer()
		//   .ofType(RegisteredValidatorParticle.class)
		//   .toWindowedSet(initialValidatorSet, RegisteredValidatorParticle.class, p -> p.getAddress(), 2)
		//   .build();

		radixEngine.addStateReducer(new ValidatorsReducer(), true);
		radixEngine.addStateReducer(new StakesReducer(), true);

		var systemCache = new SubstateCacheRegister<>(SystemParticle.class, p -> true);
		radixEngine.addSubstateCache(systemCache, true);
		radixEngine.addStateReducer(new SystemReducer(), true);

		// Additional state reducers are not required for consensus so don't need to include their
		// state in transient branches;
		logger.info("RE - Initializing stateReducers: {} {}", stateReducers, namedStateReducers);
		stateReducers.forEach(r -> radixEngine.addStateReducer(r, false));
		namedStateReducers.forEach(n -> radixEngine.addStateReducer(n.getSecond(), n.getFirst(), false));

		logger.info("RE - Initializing substate caches: {}", substateCacheRegisters);
		substateCacheRegisters.forEach(c -> radixEngine.addSubstateCache(c, false));

		return radixEngine;
	}
}
