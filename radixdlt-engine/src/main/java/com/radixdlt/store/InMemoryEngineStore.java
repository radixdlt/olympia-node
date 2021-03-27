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

import com.radixdlt.DefaultSerialization;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public final class InMemoryEngineStore<M> implements EngineStore<M> {
	private final Object lock = new Object();
	private final Map<SubstateId, Pair<CMInstruction, Atom>> storedParticles = new HashMap<>();
	private final List<Pair<Particle, Spin>> inOrderParticles = new ArrayList<>();
	private final Set<Atom> atoms = new HashSet<>();

	@Override
	public void storeAtom(Transaction txn, Atom atom) {
		synchronized (lock) {
			for (CMInstruction microInstruction : atom.getMicroInstructions()) {
				if (microInstruction.isPush()) {
					Spin nextSpin = microInstruction.getNextSpin();

					final Particle particle;
					final SubstateId substateId;
					try {
						if (microInstruction.getMicroOp() == CMInstruction.CMOp.SPIN_UP
							|| microInstruction.getMicroOp() == CMInstruction.CMOp.VIRTUAL_SPIN_DOWN) {
							particle = DefaultSerialization.getInstance().fromDson(
								microInstruction.getData(), Particle.class);
							substateId = SubstateId.ofSubstate(microInstruction.getData());
						} else if (microInstruction.getMicroOp() == CMInstruction.CMOp.SPIN_DOWN) {
							substateId = SubstateId.fromBytes(microInstruction.getData());
							var storedParticle = storedParticles.get(substateId);
							if (storedParticle == null) {
								particle = null;
							} else {
								var dson = storedParticle.getFirst().getData();
								particle = DefaultSerialization.getInstance().fromDson(dson, Particle.class);
							}
						} else {
							continue;
						}
					} catch (DeserializeException e) {
						throw new IllegalStateException();
					}

					storedParticles.put(substateId, Pair.of(microInstruction, atom));
					if (particle != null) {
						inOrderParticles.add(Pair.of(particle, nextSpin));
					}
				}
			}

			atoms.add(atom);
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
		return new Transaction() { };
	}

	@Override
	public Spin getSpin(Transaction txn, Particle particle) {
		return getSpin(SubstateId.ofSubstate(particle));
	}

	public Spin getSpin(SubstateId substateId) {
		synchronized (lock) {
			var stored = storedParticles.get(substateId);
			return stored == null ? Spin.NEUTRAL : stored.getFirst().getNextSpin();
		}
	}

	@Override
	public Optional<Particle> loadUpParticle(Transaction txn, SubstateId substateId) {
		synchronized (lock) {
			var stored = storedParticles.get(substateId);
			if (stored == null || stored.getFirst().getNextSpin() != Spin.UP) {
				return Optional.empty();
			}

			try {
				var dson = stored.getFirst().getData();
				var particle = DefaultSerialization.getInstance().fromDson(dson, Particle.class);
				return Optional.of(particle);
			} catch (DeserializeException e) {
				throw new IllegalStateException();
			}
		}
	}
}
