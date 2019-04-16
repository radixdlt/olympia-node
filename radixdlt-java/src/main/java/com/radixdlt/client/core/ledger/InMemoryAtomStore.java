package com.radixdlt.client.core.ledger;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.ledger.AtomObservation.AtomObservationUpdateType;
import io.reactivex.Completable;
import io.reactivex.CompletableEmitter;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.radixdlt.client.atommodel.accounts.RadixAddress;


/**
 * Implementation of a data store for all atoms in a shard
 */
public class InMemoryAtomStore implements AtomStore {
	private final Map<Atom, AtomObservation> atoms = new ConcurrentHashMap<>();
	private final Map<Particle, Set<Atom>> particleIndex = new ConcurrentHashMap<>();
	private final CopyOnWriteArrayList<ObservableEmitter<AtomObservation>> observers = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<ObservableEmitter<Long>> syncers = new CopyOnWriteArrayList<>();
	private final Object lock = new Object();
	private Map<RadixAddress, Boolean> syncedMap = new HashMap<>();

	private void softDeleteDependentsOf(Atom atom) {
		atom.particles(Spin.UP)
			.forEach(p -> {
				particleIndex.get(p).forEach(a -> {
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

	/**
	 * Store an atom under a given destination
	 * TODO: add synchronization if needed
	 *
	 * @param address address to store under
	 * @param atomObservation the atom to store
	 */
	public void store(RadixAddress address, AtomObservation atomObservation) {
		synchronized (lock) {
			final boolean synced = atomObservation.isHead();
			syncedMap.put(address, synced);

			final Atom atom = atomObservation.getAtom();
			if (atom != null) {

				final AtomObservation curObservation = atoms.get(atom);
				final AtomObservationUpdateType nextUpdate = atomObservation.getUpdateType();
				final AtomObservationUpdateType lastUpdate = curObservation != null ? curObservation.getUpdateType() : null;

				final boolean include;
				if (lastUpdate == null) {
					include = nextUpdate.getType() == Type.STORE;
					atom.spunParticles().forEach(s -> particleIndex
						.merge(
							s.getParticle(),
							Collections.singleton(atom),
							(a, b) -> new ImmutableSet.Builder<Atom>().addAll(a).addAll(b).build()
						)
					);
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
					observers.forEach(e -> e.onNext(atomObservation));
				}
			} else {
				observers.forEach(e -> e.onNext(atomObservation));
			}

			if (synced) {
				syncers.forEach(e -> e.onNext(System.currentTimeMillis()));
			}
		}
	}

	@Override
	public Observable<Long> onSync(RadixAddress address) {
		return Observable.create(emitter -> {
			synchronized (lock) {
				if (syncedMap.getOrDefault(address, false)) {
					emitter.onNext(System.currentTimeMillis());
				} else {
					syncers.add(emitter);
					emitter.setCancellable(() -> syncers.remove(emitter));
				}
			}
		});
	}

	@Override
	public Stream<Atom> getAtoms(RadixAddress address) {
		synchronized (lock) {
			return atoms.entrySet().stream()
				.filter(e -> e.getValue().isStore() && e.getKey().addresses().anyMatch(address::equals))
				.map(Map.Entry::getKey);
		}
	}

	@Override
	public Stream<Particle> getUpParticles(RadixAddress address) {
		synchronized (lock) {
			return particleIndex.entrySet().stream()
				.filter(e -> e.getKey().getShardables().contains(address)
					&& e.getValue().stream().flatMap(a -> {
						AtomObservation observation = atoms.get(a);
						if (observation.isStore()) {
							return observation.getAtom().spunParticles()
								.filter(s -> s.getParticle().equals(e.getKey()))
								.map(SpunParticle::getSpin);
						} else {
							return Stream.empty();
						}
					}).count() == 1
				)
				.map(Map.Entry::getKey);
		}
	}

	@Override
	public Observable<AtomObservation> getAtomObservations(RadixAddress address) {
		return Observable.create(emitter -> {
			synchronized (lock) {
				observers.add(emitter);
				atoms.entrySet().stream()
					.filter(e -> e.getValue().isStore() && e.getKey().addresses().anyMatch(address::equals))
					.map(Map.Entry::getValue)
					.forEach(emitter::onNext);
				if (syncedMap.getOrDefault(address, false)) {
					emitter.onNext(AtomObservation.head());
				}
				emitter.setCancellable(() -> {
					synchronized (lock) {
						observers.remove(emitter);
					}
				});
			}
		});
	}
}
