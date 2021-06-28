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
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.application.system.state.ValidatorBFTData;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.engine.SubstateCacheRegister;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.OptionalInt;
import java.util.Set;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	@Override
	protected void configure() {
		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<Pair<String, StateReducer<?>>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<SubstateCacheRegister<?>>() { });
	}

	@Provides
	@Singleton
	RERules reRules(
		CommittedReader committedReader, // TODO: This is a hack, remove
		Forks forks
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return forks.get(epoch);
	}

	// TODO: Remove
	@Provides
	@Singleton
	private REParser parser(RERules rules) {
		return rules.getParser();
	}

	// TODO: Remove
	@Provides
	@Singleton
	@MaxSigsPerRound
	private OptionalInt maxSigsPerRound(RERules rules) {
		return rules.getMaxSigsPerRound();
	}

	// TODO: Remove
	@Provides
	@Singleton
	@EpochCeilingView
	private View epochCeilingHighView(RERules rules) {
		return rules.getMaxRounds();
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAndBFTProof> getRadixEngine(
		EngineStore<LedgerAndBFTProof> engineStore,
		Set<StateReducer<?>> stateReducers,
		Set<Pair<String, StateReducer<?>>> namedStateReducers,
		Set<SubstateCacheRegister<?>> substateCacheRegisters,
		StakedValidatorsReducer stakedValidatorsReducer,
		RERules rules
	) {
		var cmConfig = rules.getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getVirtualStoreLayer(),
			cmConfig.getProcedures(),
			cmConfig.getMeter()
		);
		var radixEngine = new RadixEngine<>(
			rules.getParser(),
			rules.getSerialization(),
			rules.getActionConstructors(),
			cm,
			engineStore,
			rules.getBatchVerifier()
		);


		// TODO: Convert to something more like the following:
		// RadixEngine
		//   .newStateComputer()
		//   .ofType(RegisteredValidatorParticle.class)
		//   .toWindowedSet(initialValidatorSet, RegisteredValidatorParticle.class, p -> p.getAddress(), 2)
		//   .build();

		radixEngine.addStateReducer(stakedValidatorsReducer, true);
		radixEngine.addStateReducer(new SystemReducer(), true);

		var validatorsCache = new SubstateCacheRegister<>(ValidatorBFTData.class, p -> true);
		radixEngine.addSubstateCache(validatorsCache, true);
		radixEngine.addStateReducer(new CurrentValidatorsReducer(), false);

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
