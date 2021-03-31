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

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.atom.SubstateStore;
import com.radixdlt.constraintmachine.ParsedInstruction;
import com.radixdlt.constraintmachine.ParsedTransaction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<M> implements EngineStore<M>, SubstateStore {
	private final Object lock = new Object();
	private final Map<SubstateId, ParsedInstruction> storedParticles = new HashMap<>();
	private final List<Pair<Substate, Spin>> inOrderParticles = new ArrayList<>();
	private final Set<Atom> atoms = new HashSet<>();

	@Override
	public void storeAtom(Transaction txn, ParsedTransaction parsed) {
		synchronized (lock) {
			for (var instruction : parsed.instructions()) {
				storedParticles.put(instruction.getSubstate().getId(), instruction);
				inOrderParticles.add(Pair.of(instruction.getSubstate(), instruction.getSpin()));
			}

			atoms.add(parsed.getAtom());
		}
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {
		 // No-op
	}

	public boolean containsAtom(Atom atom) {
		return atoms.contains(atom);
	}

	@Override
	public <U extends Particle, V> V reduceUpParticles(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer
	) {
		V v = initial;
		synchronized (lock) {
			for (Pair<Substate, Spin> spinParticle : inOrderParticles) {
				Particle particle = spinParticle.getFirst().getParticle();
				if (particleClass.isInstance(particle)) {
					if (spinParticle.getSecond().equals(Spin.UP)) {
						v = outputReducer.apply(v, particleClass.cast(particle));
					}
				}
			}
		}
		return v;
	}

	@Override
	public Iterable<Substate> index(Class<? extends Particle> substateClass) {
		final List<Substate> substates = new ArrayList<>();
		synchronized (lock) {
			for (Pair<Substate, Spin> spinParticle : inOrderParticles) {
				var particle = spinParticle.getFirst().getParticle();
				if (spinParticle.getSecond().equals(Spin.UP) && substateClass.isInstance(particle)) {
					substates.add(spinParticle.getFirst());
				}
			}
		}
		return substates;
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
