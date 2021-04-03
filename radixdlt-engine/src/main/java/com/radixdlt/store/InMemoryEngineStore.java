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

package com.radixdlt.store;

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.RETxn;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.AID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<M> implements EngineStore<M>, SubstateStore {
	private final Object lock = new Object();
	private final Map<SubstateId, ParsedInstruction> storedParticles = new HashMap<>();
	private final Set<AID> txnIds = new HashSet<>();

	@Override
	public void storeAtom(Transaction txn, RETxn parsed) {
		synchronized (lock) {
			for (var instruction : parsed.instructions()) {
				storedParticles.put(instruction.getSubstate().getId(), instruction);
			}

			txnIds.add(parsed.getTxn().getId());
		}
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		 // No-op
	}

	public boolean containsTxn(AID txnId) {
		return txnIds.contains(txnId);
	}

	@Override
	public <U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	) {
		V v = initial;
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (i.getSpin() != Spin.UP || !particleClass.isInstance(i.getParticle())) {
					continue;
				}
				v = outputReducer.apply(v, particleClass.cast(i.getParticle()));
			}
		}
		return v;
	}

	@Override
	public Iterator<Substate> index(Class<? extends Particle> substateClass) {
		final List<Substate> substates = new ArrayList<>();
		synchronized (lock) {
			for (var i : storedParticles.values()) {
				if (i.getSpin() != Spin.UP || !substateClass.isInstance(i.getParticle())) {
					continue;
				}
				substates.add(i.getSubstate());
			}
		}

		return substates.iterator();
	}

	@Override
	public Transaction createTransaction() {
		return new Transaction() { };
	}

	@Override
	public boolean isVirtualDown(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst != null && inst.getSpin().equals(Spin.DOWN);
		}
	}

	public Spin getSpin(SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			return inst == null ? Spin.NEUTRAL : inst.getSpin();
		}
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var inst = storedParticles.get(substateId);
			if (inst == null || inst.getSpin() != Spin.UP) {
				return Optional.empty();
			}

			var particle = inst.getParticle();
			return Optional.of(particle);
		}
	}
}
