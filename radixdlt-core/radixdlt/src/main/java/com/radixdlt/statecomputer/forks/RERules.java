/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.statecomputer.forks;

import com.radixdlt.atom.REConstructor;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.engine.PostProcessor;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.ForksPostProcessor;
import com.radixdlt.statecomputer.LedgerAndBFTProof;

import java.util.OptionalInt;

public final class RERules {
	private final RERulesVersion version;
	private final REParser parser;
	private final SubstateSerialization serialization;
	private final ConstraintMachineConfig constraintMachineConfig;
	private final REConstructor actionConstructors;
	private final PostProcessor<LedgerAndBFTProof> postProcessor;
	private final RERulesConfig config;

	public RERules(
		RERulesVersion version,
		REParser parser,
		SubstateSerialization serialization,
		ConstraintMachineConfig constraintMachineConfig,
		REConstructor actionConstructors,
		PostProcessor<LedgerAndBFTProof> postProcessor,
		RERulesConfig config
	) {
		this.version = version;
		this.parser = parser;
		this.serialization = serialization;
		this.constraintMachineConfig = constraintMachineConfig;
		this.actionConstructors = actionConstructors;
		this.postProcessor = postProcessor;
		this.config = config;
	}

	public RERulesVersion getVersion() {
		return version;
	}

	public ConstraintMachineConfig getConstraintMachineConfig() {
		return constraintMachineConfig;
	}

	public SubstateSerialization getSerialization() {
		return serialization;
	}

	public REConstructor getActionConstructors() {
		return actionConstructors;
	}

	public PostProcessor<LedgerAndBFTProof> getPostProcessor() {
		return postProcessor;
	}

	public REParser getParser() {
		return parser;
	}

	public View getMaxRounds() {
		return View.of(config.getMaxRounds());
	}

	public OptionalInt getMaxSigsPerRound() {
		return config.getMaxSigsPerRound();
	}

	public int getMaxValidators() {
		return config.getMaxValidators();
	}

	public RERulesConfig getConfig() {
		return config;
	}

	public RERules withForksPostProcessor(REParser reParser, ForkConfig nextFork) {
		return new RERules(
			version,
			parser,
			serialization,
			constraintMachineConfig,
			actionConstructors,
			PostProcessor.combine(postProcessor, new ForksPostProcessor(reParser, nextFork)),
			config
		);
	}
}
