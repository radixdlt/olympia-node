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

import com.radixdlt.atom.Substate;
import com.radixdlt.atom.TxLowLevelBuilder;
import com.radixdlt.atom.Atom;
import com.radixdlt.client.core.atoms.Addresses;
import com.radixdlt.constraintmachine.Spin;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.annotations.Nullable;
import java.util.Collections;
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
	private final Map<Substate, Map<Spin, Set<Atom>>> particleIndex = Collections.synchronizedMap(new LinkedHashMap<>());

	private final Map<RadixAddress, CopyOnWriteArrayList<ObservableEmitter<AtomObservation>>> allObservers = new ConcurrentHashMap<>();
	private final Map<RadixAddress, CopyOnWriteArrayList<ObservableEmitter<Long>>> allSyncers = new ConcurrentHashMap<>();

	private final Object lock = new Object();
	private final Map<RadixAddress, Boolean> syncedMap = new HashMap<>();

	private final Map<String, TxLowLevelBuilder> stagedAtoms = new ConcurrentHashMap<>();
	private final Map<String, Map<Substate, Spin>> stagedParticleIndex = new ConcurrentHashMap<>();

	@Override
	public TxLowLevelBuilder getStaged(String uuid) {
		Objects.requireNonNull(uuid);

		synchronized (lock) {
			return stagedAtoms.get(uuid);
		}
	}

	@Override
	public TxLowLevelBuilder getStagedAndClear(String uuid) {
		Objects.requireNonNull(uuid);

		synchronized (lock) {
			final TxLowLevelBuilder builder = stagedAtoms.remove(uuid);
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
	public Stream<Substate> getUpParticles(RadixAddress address, @Nullable String stagedUuid) {
		synchronized (lock) {
			List<Substate> upParticles = particleIndex.entrySet().stream()
				.filter(e -> Addresses.getShardables(e.getKey().getParticle()).contains(address))
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
					.filter(s -> Addresses.getShardables(s.getParticle()).contains(address))
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
