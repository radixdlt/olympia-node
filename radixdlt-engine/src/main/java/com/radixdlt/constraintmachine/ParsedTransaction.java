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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.serialization.DsonOutput;

import java.util.List;
import java.util.stream.Stream;

/**
 * Transaction which has been successfully parsed and state checked by radix engine
 */
public final class ParsedTransaction {
	private final Atom atom;
	private final List<ParsedInstruction> instructions;

	public ParsedTransaction(Atom atom, List<ParsedInstruction> instructions) {
		this.atom = atom;
		this.instructions = instructions;
	}

	// Hack, remove later
	private static AID atomIdOf(Atom atom) {
		var dson = DefaultSerialization.getInstance().toDson(atom, DsonOutput.Output.ALL);
		var firstHash = HashUtils.sha256(dson);
		var secondHash = HashUtils.sha256(firstHash.asBytes());
		return AID.from(secondHash.asBytes());
	}

	public Atom getAtom() {
		return atom;
	}

	public AID getAtomId() {
		return atomIdOf(atom);
	}

	public boolean isUserCommand() {
		return instructions.stream().noneMatch(i -> i.getSubstate().getParticle() instanceof SystemParticle);
	}

	public List<ParsedInstruction> instructions() {
		return instructions;
	}

	public Stream<Particle> upSubstates() {
		return instructions.stream()
			.filter(ParsedInstruction::isUp)
			.map(ParsedInstruction::getSubstate)
			.map(Substate::getParticle);
	}
}
