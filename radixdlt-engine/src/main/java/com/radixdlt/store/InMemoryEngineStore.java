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

import com.google.common.hash.HashCode;
import com.radixdlt.DefaultSerialization;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<T extends RadixEngineAtom, M> implements EngineStore<T, M> {
	private final Object lock = new Object();
	private final Map<HashCode, Pair<CMMicroInstruction, T>> storedParticles = new HashMap<>();
	private final List<Pair<Particle, Spin>> inOrderParticles = new ArrayList<>();
	private final Set<T> atoms = new HashSet<>();
	private final Serialization serialization = DefaultSerialization.getInstance();

	@Override
	public void storeAtom(Transaction txn, T atom) {
		synchronized (lock) {
			for (CMMicroInstruction microInstruction : atom.getCMInstruction().getMicroInstructions()) {
				if (microInstruction.isPush()) {
					Spin nextSpin = microInstruction.getNextSpin();
					var particle = microInstruction.getParticle();
					final HashCode particleHash;
					if (particle != null) {
						particleHash = HashUtils.sha256(serialization.toDson(particle, DsonOutput.Output.ALL));
					} else {
						particleHash = microInstruction.getParticleHash();
					}
					storedParticles.put(
						particleHash,
						Pair.of(microInstruction, atom)
					);
					inOrderParticles.add(Pair.of(microInstruction.getParticle(), nextSpin));
				}
			}

			atoms.add(atom);
		}
	}

	@Override
	public void storeMetadata(Transaction txn, M metadata) {

	}

	@Override
	public boolean containsAtom(T atom) {
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
			for (Pair<Particle, Spin> spinParticle : inOrderParticles) {
				Particle particle = spinParticle.getFirst();
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
	public Transaction createTransaction() {
		return new Transaction() {
			@Override
			public void commit() {
			}

			@Override
			public void abort() {
			}

			@Override
			public <T> T unwrap() {
				return null;
			}
		};
	}

	@Override
	public Spin getSpin(Transaction txn, Particle particle) {
		var particleHash = HashUtils.sha256(serialization.toDson(particle, DsonOutput.Output.ALL));
		return getSpin(particleHash);
	}

	public Spin getSpin(HashCode particleHash) {
		synchronized (lock) {
			var stored = storedParticles.get(particleHash);
			return stored == null ? Spin.NEUTRAL : stored.getFirst().getNextSpin();
		}
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, HashCode particleHash) {
		synchronized (lock) {
			var stored = storedParticles.get(particleHash);
			if (stored == null || stored.getFirst().getNextSpin() != Spin.UP) {
				return Optional.empty();
			}

			return Optional.of(stored.getFirst().getParticle());
		}
	}

}
