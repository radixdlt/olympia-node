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

package com.radixdlt.constraintmachine;

import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Optional;

public class REParsedAction {
	private final List<REParsedInstruction> instructions;
	private final Pair<Particle, UsedData> deallocated;

	private REParsedAction(List<REParsedInstruction> instructions, Pair<Particle, UsedData> deallocated) {
		this.instructions = instructions;
		this.deallocated = deallocated;
	}

	public static REParsedAction create(
		List<REParsedInstruction> instructions,
		Pair<Particle, UsedData> deallocated
	) {
		return new REParsedAction(instructions, deallocated);
	}

	public List<REParsedInstruction> getInstructions() {
		return instructions;
	}

	public Optional<Pair<Particle, UsedData>> getDeallocated() {
		return Optional.ofNullable(deallocated);
	}
}
