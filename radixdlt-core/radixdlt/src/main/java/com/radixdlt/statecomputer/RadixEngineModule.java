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
import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorEpochData;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.engine.PostProcessedVerifier;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.engine.SubstateCacheRegister;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import com.radixdlt.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.TreeMap;

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
	@EpochCeilingView
	private View epochCeilingHighView(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getEpochCeilingView();
	}

	@Provides
	@Singleton
	private ConstraintMachineConfig buildConstraintMachineConfig(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getConstraintMachineConfig();
	}

	@Provides
	@Singleton
	private ConstraintMachine constraintMachine(
		ConstraintMachineConfig config
	) {
		return new ConstraintMachine(
			config.getVirtualStoreLayer(),
			config.getProcedures(),
			config.getMetering()
		);
	}

	@Provides
	@Singleton
	private ActionConstructors actionConstructors(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getActionConstructors();
	}

	@Provides
	@Singleton
	private BatchVerifier<LedgerAndBFTProof> batchVerifier(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getBatchVerifier();
	}

	@Provides
	@Singleton
	private REParser parser(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getParser();
	}

	@Provides
	@Singleton
	private SubstateSerialization substateSerialization(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getSubstateSerialization();
	}


	@Provides
	PostProcessedVerifier checker(
		CommittedReader committedReader, // TODO: This is a hack, remove
		TreeMap<Long, ForkConfig> epochToForkConfig
	) {
		var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		return epochToForkConfig.floorEntry(epoch).getValue().getPostProcessedVerifier();
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAndBFTProof> getRadixEngine(
		REParser parser,
		SubstateSerialization serialization,
		ConstraintMachine constraintMachine,
		ActionConstructors actionConstructors,
		EngineStore<LedgerAndBFTProof> engineStore,
		PostProcessedVerifier checker,
		BatchVerifier<LedgerAndBFTProof> batchVerifier,
		Set<StateReducer<?>> stateReducers,
		Set<Pair<String, StateReducer<?>>> namedStateReducers,
		Set<SubstateCacheRegister<?>> substateCacheRegisters,
		StakedValidatorsReducer stakedValidatorsReducer
	) {
		var radixEngine = new RadixEngine<>(
			parser,
			serialization,
			actionConstructors,
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

		radixEngine.addStateReducer(stakedValidatorsReducer, true);

		var systemCache = new SubstateCacheRegister<>(SystemParticle.class, p -> true);
		radixEngine.addSubstateCache(systemCache, true);
		radixEngine.addStateReducer(new SystemReducer(), true);

		var validatorsCache = new SubstateCacheRegister<>(ValidatorEpochData.class, p -> true);
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
