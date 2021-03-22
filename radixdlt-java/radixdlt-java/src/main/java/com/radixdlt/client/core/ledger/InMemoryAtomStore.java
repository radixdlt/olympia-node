/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.ledger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atom.AtomBuilder;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.client.core.atoms.Addresses;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.ledger.AtomObservation.AtomObservationUpdateType;
import com.radixdlt.constraintmachine.Spin;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.annotations.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.radixdlt.identifiers.RadixAddress;

/**
 * An in memory storage of atoms and particles
 * TODO @Performance: revisit the use of ordered, deterministic data structures (e.g. synchronized linked maps)
 */
public class InMemoryAtomStore implements AtomStore {
	private final Map<Atom, AtomObservation> atoms = new ConcurrentHashMap<>();
	private final Map<Particle, Map<Spin, Set<Atom>>> particleIndex = Collections.synchronizedMap(new LinkedHashMap<>());

	private final Map<RadixAddress, CopyOnWriteArrayList<ObservableEmitter<AtomObservation>>> allObservers = new ConcurrentHashMap<>();
	private final Map<RadixAddress, CopyOnWriteArrayList<ObservableEmitter<Long>>> allSyncers = new ConcurrentHashMap<>();

	private final Object lock = new Object();
	private final Map<RadixAddress, Boolean> syncedMap = new HashMap<>();

	private final Map<String, AtomBuilder> stagedAtoms = new ConcurrentHashMap<>();
	private final Map<String, Map<Particle, Spin>> stagedParticleIndex = new ConcurrentHashMap<>();

	private void softDeleteDependentsOf(Atom atom) {
		atom.upParticles()
			.forEach(p -> {
				Map<Spin, Set<Atom>> particleSpinIndex = particleIndex.get(p);
				particleSpinIndex.getOrDefault(Spin.DOWN, Set.of())
					.forEach(a -> {
						AtomObservation observation = atoms.get(a);
						if (observation.getAtom().equals(atom)) {
							return;
						}

						if (observation.getUpdateType().getType() == Type.STORE || !observation.getUpdateType().isSoft()) {
							// This first so that leaves get deleted first
							softDeleteDependentsOf(observation.getAtom());

							atoms.put(observation.getAtom(), AtomObservation.softDeleted(observation.getAtom()));
						}
					});
			});
	}

	@Override
	public void stageParticleGroup(String uuid, ParticleGroup particleGroup) {
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(particleGroup);

		synchronized (lock) {
			var stagedAtom = stagedAtoms.get(uuid);
			if (stagedAtom == null) {
				stagedAtom = Atom.newBuilder().addParticleGroup(particleGroup);
				stagedAtoms.put(uuid, stagedAtom);
			} else {
				stagedAtom.addParticleGroup(particleGroup);
			}

			particleGroup.upParticles().forEach(p -> {
				Map<Particle, Spin> index = stagedParticleIndex.getOrDefault(uuid, new LinkedHashMap<>());
				index.put(p, Spin.UP);
				stagedParticleIndex.put(uuid, index);
			});
		}
	}

	@Override
	public AtomBuilder getStaged(String uuid) {
		Objects.requireNonNull(uuid);

		synchronized (lock) {
			return stagedAtoms.get(uuid);
		}
	}

	@Override
	public AtomBuilder getStagedAndClear(String uuid) {
		Objects.requireNonNull(uuid);

		synchronized (lock) {
			final AtomBuilder builder = stagedAtoms.remove(uuid);
			stagedParticleIndex.get(uuid).clear();
			return builder;
		}
	}

	/**
	 * Store an atom under a given destination
	 *
	 * @param address address to store under
	 * @param atomObservation the atom to store
	 */
	public void store(RadixAddress address, AtomObservation atomObservation) {
		synchronized (lock) {
			final boolean synced = atomObservation.isHead();
			syncedMap.put(address, synced);

			final var atom = atomObservation.getAtom();
			if (atom != null) {

				final AtomObservation curObservation = atoms.get(atom);
				final AtomObservationUpdateType nextUpdate = atomObservation.getUpdateType();
				final AtomObservationUpdateType lastUpdate = curObservation != null ? curObservation.getUpdateType() : null;

				// If a new hard observed atoms conflicts with a previously stored atom,
				// stored atom must be deleted
				if (nextUpdate.getType() == Type.STORE && !nextUpdate.isSoft()) {
					atom.uniqueInstructions().forEach(i -> {
						var spinParticleIndex = particleIndex.getOrDefault(i.getParticle(), Map.of());
						spinParticleIndex.getOrDefault(i.getNextSpin(), Set.of())
							.forEach(a -> {
								if (a.equals(atom)) {
									return;
								}
								AtomObservation oldObservation = atoms.get(a);
								if (oldObservation.isStore()) {
									softDeleteDependentsOf(a);
									atoms.put(a, AtomObservation.softDeleted(a));
								}
							});
					});
				}

				final boolean include;
				if (lastUpdate == null) {
					include = nextUpdate.getType() == Type.STORE;
					atom.uniqueInstructions().forEach(i -> {
						Map<Spin, Set<Atom>> spinParticleIndex = particleIndex.get(i.getParticle());
						if (spinParticleIndex == null) {
							spinParticleIndex = new EnumMap<>(Spin.class);
							particleIndex.put(i.getParticle(), spinParticleIndex);
						}
						spinParticleIndex.merge(
							i.getNextSpin(),
							Collections.singleton(atom),
							(a, b) -> new ImmutableSet.Builder<Atom>().addAll(a).addAll(b).build()
						);
					});
				} else {
					// Soft observation should not be able to update a hard state
					// Only update if type changes
					include = (!nextUpdate.isSoft() || lastUpdate.isSoft())
						&& nextUpdate.getType() != lastUpdate.getType();
				}

				if (nextUpdate.getType() == Type.DELETE && include) {
					softDeleteDependentsOf(atom);
				}

				final boolean isSoftToHard = lastUpdate != null && lastUpdate.isSoft() && !nextUpdate.isSoft();
				if (include || isSoftToHard) {
					atoms.put(atom, atomObservation);
				}

				if (include) {
					Addresses.ofAtom(atom).forEach(addr -> {
						final CopyOnWriteArrayList<ObservableEmitter<AtomObservation>> observers = allObservers.get(addr);
						if (observers != null) {
							observers.forEach(e -> e.onNext(atomObservation));
						}
					});
				}
			} else {
				final CopyOnWriteArrayList<ObservableEmitter<AtomObservation>> observers = allObservers.get(address);
				if (observers != null) {
					observers.forEach(e -> e.onNext(atomObservation));
				}
			}

			if (synced) {
				final CopyOnWriteArrayList<ObservableEmitter<Long>> syncers = allSyncers.get(address);
				if (syncers != null) {
					syncers.forEach(e -> e.onNext(System.currentTimeMillis()));
				}
			}
		}
	}

	@Override
	public Observable<Long> onSync(RadixAddress address) {
		return Observable.create(emitter -> {
			synchronized (lock) {
				if (syncedMap.getOrDefault(address, false)) {
					emitter.onNext(System.currentTimeMillis());
				}

				final CopyOnWriteArrayList<ObservableEmitter<Long>> syncers;
				if (!allSyncers.containsKey(address)) {
					syncers = new CopyOnWriteArrayList<>();
					allSyncers.put(address, syncers);
				} else {
					syncers = allSyncers.get(address);
				}
				syncers.add(emitter);
				emitter.setCancellable(() -> syncers.remove(emitter));
			}
		});
	}

	@Override
	public Stream<Atom> getStoredAtoms(RadixAddress address) {
		synchronized (lock) {
			return atoms.entrySet().stream()
				.filter(e -> e.getValue().isStore() && Addresses.ofAtom(e.getKey()).anyMatch(address::equals))
				.map(Map.Entry::getValue)
				.map(AtomObservation::getAtom);
		}
	}


	@Override
	public Stream<Particle> getUpParticles(RadixAddress address, @Nullable String stagedUuid) {
		synchronized (lock) {
			List<Particle> upParticles = particleIndex.entrySet().stream()
				.filter(e -> Addresses.getShardables(e.getKey()).contains(address))
				.filter(e -> {
					final Map<Spin, Set<Atom>> spinParticleIndex = e.getValue();
					final boolean hasDown = spinParticleIndex.getOrDefault(Spin.DOWN, Set.of())
						.stream().anyMatch(a -> atoms.get(a).isStore());
					if (hasDown) {
						return false;
					}

					if (stagedUuid != null
						&& stagedParticleIndex.getOrDefault(stagedUuid, Map.of()).get(e.getKey()) == Spin.DOWN) {
						return false;
					}

					Set<Atom> uppingAtoms = spinParticleIndex.getOrDefault(Spin.UP, Set.of());
					return uppingAtoms.stream().anyMatch(a -> atoms.get(a).isStore());
				})
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

			if (stagedUuid != null) {
				stagedParticleIndex.getOrDefault(stagedUuid, Map.of()).entrySet().stream()
					.filter(e -> e.getValue() == Spin.UP)
					.map(Entry::getKey)
					.filter(p -> Addresses.getShardables(p).contains(address))
					.forEach(upParticles::add);
			}


			return upParticles.stream();
		}
	}

	@Override
	public Observable<AtomObservation> getAtomObservations(RadixAddress address) {
		return Observable.create(emitter -> {
			synchronized (lock) {
				final CopyOnWriteArrayList<ObservableEmitter<AtomObservation>> observers;
				if (!allObservers.containsKey(address)) {
					observers = new CopyOnWriteArrayList<>();
					allObservers.put(address, observers);
				} else {
					observers = allObservers.get(address);
				}
				observers.add(emitter);
				atoms.entrySet().stream()
					.filter(e -> e.getValue().isStore() && Addresses.ofAtom(e.getKey()).anyMatch(address::equals))
					.map(Map.Entry::getValue)
					.forEach(emitter::onNext);

				emitter.setCancellable(() -> {
					synchronized (lock) {
						observers.remove(emitter);
						if (observers.isEmpty()) {
							this.allObservers.remove(address);
						}
					}
				});
			}
		});
	}
}
