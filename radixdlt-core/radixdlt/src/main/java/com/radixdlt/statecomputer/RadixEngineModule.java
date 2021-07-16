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
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import com.radixdlt.store.EngineStore;
import com.radixdlt.sync.CommittedReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.OptionalInt;

/**
 * Module which manages execution of commands
 */
public class RadixEngineModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

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

	// TODO: Remove
	@Provides
	@Singleton
	@MaxValidators
	private int maxValidators(RERules rules) {
		return rules.getMaxValidators();
	}

	@Provides
	@Singleton
	private RadixEngine<LedgerAndBFTProof> getRadixEngine(
		EngineStore<LedgerAndBFTProof> engineStore,
		RERules rules
	) {
		var cmConfig = rules.getConstraintMachineConfig();
		var cm = new ConstraintMachine(
			cmConfig.getProcedures(),
			cmConfig.getDeserialization(),
			cmConfig.getVirtualSubstateDeserialization(),
			cmConfig.getMeter()
		);
		return new RadixEngine<>(
			rules.getParser(),
			rules.getSerialization(),
			rules.getActionConstructors(),
			cm,
			engineStore,
			rules.getBatchVerifier()
		);
	}
}
