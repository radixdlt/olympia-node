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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.Txn;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.utils.Pair;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Transaction which has been successfully parsed and state checked by radix engine
 */
public final class REParsedTxn {
	private final Txn txn;
	private final List<REParsedAction> actions;

	public REParsedTxn(Txn txn, List<REParsedAction> actions) {
		this.txn = txn;
		this.actions = actions;
	}

	public List<REParsedAction> getActions() {
		return actions;
	}

	public Txn getTxn() {
		return txn;
	}

	public boolean isUserCommand() {
		return actions.stream().flatMap(a -> a.getInstructions().stream())
			.noneMatch(i -> i.getSubstate().getParticle() instanceof SystemParticle);
	}

	public Stream<Pair<Particle, ReducerState>> deallocated() {
		return actions.stream().flatMap(a -> a.getDeallocated().stream());
	}

	public Stream<REParsedInstruction> instructions() {
		return actions.stream().flatMap(a -> a.getInstructions().stream());
	}

	public Stream<Particle> upSubstates() {
		return instructions()
			.filter(REParsedInstruction::isBootUp)
			.map(REParsedInstruction::getSubstate)
			.map(Substate::getParticle);
	}

	@Override
	public int hashCode() {
		return Objects.hash(txn, actions);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof REParsedTxn)) {
			return false;
		}

		var other = (REParsedTxn) o;
		return Objects.equals(this.txn, other.txn)
			&& Objects.equals(this.actions, other.actions);
	}
}
