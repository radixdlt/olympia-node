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
import com.radixdlt.atom.Substate;
import com.radixdlt.atom.SubstateId;
import com.radixdlt.constraintmachine.REInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.DeserializeException;
import com.radixdlt.utils.Ints;
import com.radixdlt.utils.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class InMemoryEngineStore<M> implements EngineStore<M> {
	private final Object lock = new Object();
	private final Map<SubstateId, Pair<REInstruction, Atom>> storedParticles = new HashMap<>();
	private final List<Pair<Substate, Spin>> inOrderParticles = new ArrayList<>();
	private final Set<Atom> atoms = new HashSet<>();

	@Override
	public void storeAtom(Transaction txn, Atom atom) {
		synchronized (lock) {
			for (int i = 0; i < atom.getMicroInstructions().size(); i++) {
				var instruction = atom.getMicroInstructions().get(i);
				if (instruction.isPush()) {
					Spin nextSpin = instruction.getNextSpin();

					final Particle particle;
					final SubstateId substateId;
					try {
						if (instruction.getMicroOp() == REInstruction.REOp.UP) {
							particle = DefaultSerialization.getInstance().fromDson(instruction.getData(), Particle.class);
							substateId = SubstateId.ofSubstate(atom, i);
						} else if (instruction.getMicroOp() == REInstruction.REOp.VDOWN) {
							particle = DefaultSerialization.getInstance().fromDson(instruction.getData(), Particle.class);
							substateId = SubstateId.ofVirtualSubstate(instruction.getData());
						} else if (instruction.getMicroOp() == REInstruction.REOp.DOWN) {
							substateId = SubstateId.fromBytes(instruction.getData());
							var storedParticle = storedParticles.get(substateId);
							if (storedParticle == null) {
								particle = null;
							} else {
								var dson = storedParticle.getFirst().getData();
								particle = DefaultSerialization.getInstance().fromDson(dson, Particle.class);
							}
						} else if (instruction.getMicroOp() == REInstruction.REOp.LDOWN) {
							int index = Ints.fromByteArray(instruction.getData());
							var dson = atom.getMicroInstructions().get(index).getData();
							particle = DefaultSerialization.getInstance().fromDson(dson, Particle.class);
							substateId = SubstateId.ofSubstate(atom, index);
						} else {
							throw new IllegalStateException("Unknown op " + instruction.getMicroOp());
						}
					} catch (DeserializeException e) {
						throw new IllegalStateException();
					}

					storedParticles.put(substateId, Pair.of(instruction, atom));
					if (particle != null) {
						inOrderParticles.add(Pair.of(Substate.create(particle, substateId), nextSpin));
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
	public <U extends Particle> Iterable<Substate> upSubstates(Class<U> substateClass, Predicate<U> substatePredicate) {
		final List<Substate> substates = new ArrayList<>();
		synchronized (lock) {
			for (Pair<Substate, Spin> spinParticle : inOrderParticles) {
				var particle = spinParticle.getFirst().getParticle();
				if (spinParticle.getSecond().equals(Spin.UP)
					&& substateClass.isInstance(particle)
					&& substatePredicate.test(substateClass.cast(particle))
				) {
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
			var stored = storedParticles.get(substateId);
			return stored != null && stored.getFirst().getNextSpin().equals(Spin.DOWN);
		}
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
