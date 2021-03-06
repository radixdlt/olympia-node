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

import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<T extends RadixEngineAtom> implements EngineStore<T> {
	private final Object lock = new Object();
	private final Map<Particle, Pair<Spin, T>> storedParticles = new HashMap<>();
	private final List<Pair<Particle, Spin>> inOrderParticles = new ArrayList<>();
	private final Set<T> atoms = new HashSet<>();

	@Override
	public void storeAtom(T atom) {
		synchronized (lock) {
			for (CMMicroInstruction microInstruction : atom.getCMInstruction().getMicroInstructions()) {
				if (microInstruction.isPush()) {
					Spin nextSpin = microInstruction.getNextSpin();
					storedParticles.put(
						microInstruction.getParticle(),
						Pair.of(nextSpin, atom)
					);
					inOrderParticles.add(Pair.of(microInstruction.getParticle(), nextSpin));
				}
			}

			atoms.add(atom);
		}
	}

	@Override
	public boolean containsAtom(T atom) {
		return atoms.contains(atom);
	}

	@Override
	public <U extends Particle, V> V compute(
		Class<U> particleClass,
		V initial,
		BiFunction<V, U, V> outputReducer,
		BiFunction<V, U, V> inputReducer
	) {
		V v = initial;
		synchronized (lock) {
			for (Pair<Particle, Spin> spinParticle : inOrderParticles) {
				Particle particle = spinParticle.getFirst();
				if (particleClass.isInstance(particle)) {
					if (spinParticle.getSecond().equals(Spin.UP)) {
						v = outputReducer.apply(v, particleClass.cast(particle));
					} else {
						v = inputReducer.apply(v, particleClass.cast(particle));
					}
				}
			}
		}
		return v;
	}

	@Override
	public Spin getSpin(Particle particle) {
		synchronized (lock) {
			Pair<Spin, T> stored = storedParticles.get(particle);
			return stored == null ? Spin.NEUTRAL : stored.getFirst();
		}
	}
}
