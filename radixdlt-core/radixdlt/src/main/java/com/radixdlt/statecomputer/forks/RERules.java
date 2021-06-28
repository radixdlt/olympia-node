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

import com.radixdlt.atom.ActionConstructors;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.constraintmachine.ConstraintMachineConfig;
import com.radixdlt.constraintmachine.SubstateSerialization;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.engine.parser.REParser;
import com.radixdlt.statecomputer.LedgerAndBFTProof;

public class RERules {
	private final REParser parser;
	private final SubstateSerialization serialization;
	private final ConstraintMachineConfig constraintMachineConfig;
	private final ActionConstructors actionConstructors;
	private final BatchVerifier<LedgerAndBFTProof> batchVerifier;
	private final View maxRounds;

	public RERules(
		REParser parser,
		SubstateSerialization serialization,
		ConstraintMachineConfig constraintMachineConfig,
		ActionConstructors actionConstructors,
		BatchVerifier<LedgerAndBFTProof> batchVerifier,
		View maxRounds
	) {
		this.parser = parser;
		this.serialization = serialization;
		this.constraintMachineConfig = constraintMachineConfig;
		this.actionConstructors = actionConstructors;
		this.batchVerifier = batchVerifier;
		this.maxRounds = maxRounds;
	}

	public ConstraintMachineConfig getConstraintMachineConfig() {
		return constraintMachineConfig;
	}

	public SubstateSerialization getSerialization() {
		return serialization;
	}

	public ActionConstructors getActionConstructors() {
		return actionConstructors;
	}

	public BatchVerifier<LedgerAndBFTProof> getBatchVerifier() {
		return batchVerifier;
	}

	public REParser getParser() {
		return parser;
	}

	public View getMaxRounds() {
		return maxRounds;
	}
}
