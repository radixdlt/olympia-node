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
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.engine.BatchVerifier;
import com.radixdlt.statecomputer.LedgerAndBFTProof;

/**
 * Configuration used for hard forks
 */
public final class ForkConfig {
	private final String name;
	private final ConstraintMachine constraintMachine;
	private final ActionConstructors actionConstructors;
	private final BatchVerifier<LedgerAndBFTProof> batchVerifier;
	private final View epochCeilingView;

	public ForkConfig(
		String name,
		ConstraintMachine constraintMachine,
		ActionConstructors actionConstructors,
		BatchVerifier<LedgerAndBFTProof> batchVerifier,
		View epochCeilingView
	) {
		this.name = name;
		this.constraintMachine = constraintMachine;
		this.actionConstructors = actionConstructors;
		this.batchVerifier = batchVerifier;
		this.epochCeilingView = epochCeilingView;
	}

	public String getName() {
		return name;
	}

	public ConstraintMachine getConstraintMachine() {
		return constraintMachine;
	}

	public ActionConstructors getActionConstructors() {
		return actionConstructors;
	}

	public BatchVerifier<LedgerAndBFTProof> getBatchVerifier() {
		return batchVerifier;
	}

	public View getEpochCeilingView() {
		return epochCeilingView;
	}
}
