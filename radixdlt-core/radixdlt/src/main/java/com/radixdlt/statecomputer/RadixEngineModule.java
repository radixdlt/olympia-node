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
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.ForkConfig;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.ForksEpochStore;
import com.radixdlt.statecomputer.forks.InitialForkConfig;
import com.radixdlt.statecomputer.forks.LatestKnownForkConfig;
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
	}

	@Provides
	@Singleton
	@InitialForkConfig
	private ForkConfig initialForkConfig(
		EngineStore<LedgerAndBFTProof> engineStore,
		CommittedReader committedReader,
		ForksEpochStore forksEpochStore,
		Forks forks
	) {
		forks.tryExecuteMissedFork(engineStore, committedReader, forksEpochStore);
		forks.sanityCheck(committedReader, forksEpochStore);
		return forks.getCurrentFork(forksEpochStore.getEpochsForkHashes());
	}

	@Provides
	@Singleton
	@LatestKnownForkConfig
	private ForkConfig latestKnownForkConfig(Forks forks) {
		return forks.latestKnownFork();
	}

	// TODO: Remove
	@Provides
	@Singleton
	private REParser parser(@InitialForkConfig ForkConfig forkConfig) {
		return forkConfig.engineRules().getParser();
	}

	// TODO: Remove
	@Provides
	@Singleton
	@MaxSigsPerRound
	private OptionalInt maxSigsPerRound(@InitialForkConfig ForkConfig forkConfig) {
		return forkConfig.engineRules().getMaxSigsPerRound();
	}

	// TODO: Remove
	@Provides
	@Singleton
	@EpochCeilingView
	private View epochCeilingHighView(@InitialForkConfig ForkConfig forkConfig) {
		return forkConfig.engineRules().getMaxRounds();
	}

	// TODO: Remove
	@Provides
	@Singleton
	@MaxValidators
	private int maxValidators(@InitialForkConfig ForkConfig forkConfig) {
		return forkConfig.engineRules().getMaxValidators();
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAndBFTProof> getRadixEngine(
		EngineStore<LedgerAndBFTProof> engineStore,
		Set<StateReducer<?>> stateReducers,
		Set<Pair<String, StateReducer<?>>> namedStateReducers,
		@InitialForkConfig ForkConfig forkConfig
	) {
		final var cmConfig = forkConfig.engineRules().getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getProcedures(),
			cmConfig.getDeserialization(),
			cmConfig.getVirtualSubstateDeserialization(),
			cmConfig.getMeter()
		);
		final var radixEngine = new RadixEngine<>(
			forkConfig.engineRules().getParser(),
			forkConfig.engineRules().getSerialization(),
			forkConfig.engineRules().getActionConstructors(),
			cm,
			engineStore,
			forkConfig.engineRules().getBatchVerifier()
		);

		radixEngine.addStateReducer(new CurrentValidatorsReducer(), false);

		// Additional state reducers are not required for consensus so don't need to include their
		// state in transient branches;
		logger.info("RE - Initializing stateReducers: {} {}", stateReducers, namedStateReducers);
		stateReducers.forEach(r -> radixEngine.addStateReducer(r, false));
		namedStateReducers.forEach(n -> radixEngine.addStateReducer(n.getSecond(), n.getFirst(), false));

		return radixEngine;
	}
}
