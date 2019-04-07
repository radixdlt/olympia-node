package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.ledger.AtomObservation.AtomObservationUpdateType;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;
import io.reactivex.subjects.ReplaySubject;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.radix.common.ID.EUID;

/**
 * Implementation of a data store for all atoms in a shard
 */
public class InMemoryAtomStore implements AtomStore {

	/**
	 * The In Memory Atom Data Store
	 */
	private final ConcurrentHashMap<RadixAddress, Subject<AtomObservation>> cache = new ConcurrentHashMap<>();

	/**
	 * Total observation observationCountPerAddress per address
	 */
	private final ConcurrentHashMap<RadixAddress, Long> observationCountPerAddress = new ConcurrentHashMap<>();

	/**
	 * Store an atom under a given destination
	 * TODO: add synchronization if needed
	 *
	 * @param address address to store under
	 * @param atomObservation the atom to store
	 */
	public void store(RadixAddress address, AtomObservation atomObservation) {
		observationCountPerAddress.merge(address, 1L, Long::sum);
		cache.computeIfAbsent(address, addr -> ReplaySubject.<AtomObservation>create().toSerialized()).onNext(atomObservation);
	}

	private static class AtomObservationsState {
		private final ConcurrentHashMap<EUID, AtomObservation> curAtomState = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<EUID, EUID> downParticlesToAtoms = new ConcurrentHashMap<>();
		private long curCount;

		AtomObservationsState() {
			this.curCount = 0;
		}

		AtomObservationUpdateType get(Atom atom) {
			AtomObservation o = curAtomState.get(atom.getHid());
			return o == null ? null : o.getUpdateType();
		}

		void put(AtomObservation atomObservation) {
			curAtomState.put(atomObservation.getAtom().getHid(), atomObservation);

			if (atomObservation.isStore()) {
				atomObservation.getAtom().particles(Spin.DOWN)
					.forEach(p -> downParticlesToAtoms.put(p.getHid(), atomObservation.getAtom().getHid()));
			}
		}

		Optional<AtomObservation> atomContainingDown(Particle p) {
			return Optional.ofNullable(downParticlesToAtoms.get(p.getHid())).map(curAtomState::get);
		}
	}

	/**
	 * Returns an unending stream of validated atoms which are stored at a particular destination.
	 *
	 * @param address address (which determines shard) to query atoms for
	 * @return an Atom Observable
	 */
	@Override
	public Observable<AtomObservation> getAtoms(RadixAddress address) {
		Objects.requireNonNull(address);
		return Observable.fromCallable(AtomObservationsState::new)
			.flatMap(atomsObservationState -> cache.computeIfAbsent(address, addr -> ReplaySubject.<AtomObservation>create().toSerialized())
				.concatMap(observation -> {
					atomsObservationState.curCount++;

					if (observation.getAtom() != null) {
						AtomObservationUpdateType nextUpdate = observation.getUpdateType();
						AtomObservationUpdateType lastUpdate = atomsObservationState.get(observation.getAtom());


						final boolean include;
						if (lastUpdate == null) {
							include = nextUpdate.getType() == Type.STORE;
						} else {
							// Soft observation should not be able to update a hard state
							// Only update if type changes
							include = (!nextUpdate.isSoft() || lastUpdate.isSoft())
								&& nextUpdate.getType() != lastUpdate.getType();
						}

						// Core currently does not guarantee DELETEs being emitted in order so need this hack for now
						// That is, create soft deletes for any known missing deletes from dependent atoms
						List<AtomObservation> missingDeletes = new ArrayList<>();
						if (nextUpdate.getType() == Type.DELETE && include) {
							// TODO: Should we DELETE atoms which are dependent on these atoms as well?
							List<AtomObservation> observationsContainingDown = observation.getAtom().particles(Spin.UP)
								.flatMap(p -> atomsObservationState.atomContainingDown(p)
									.map(o -> o.isStore() && !o.getAtom().getHid().equals(observation.getAtom().getHid())
										? Stream.of(o) : Stream.<AtomObservation>empty())
									.orElse(Stream.empty())
								)
								.collect(Collectors.toList());

							if (!observationsContainingDown.isEmpty()) {
								observationsContainingDown.forEach(o -> missingDeletes.add(AtomObservation.softDeleted(o.getAtom())));
							}
						}

						// Should always update observation state if going from soft to hard observation
						final boolean isSoftToHard = lastUpdate != null && lastUpdate.isSoft() && !nextUpdate.isSoft();

						if (include || isSoftToHard) {
							atomsObservationState.put(observation);
							missingDeletes.forEach(atomsObservationState::put);
						}

						return include ? Observable.fromIterable(missingDeletes).concatWith(Observable.just(observation)) : Observable.empty();
					} else if (observation.isHead()) {
						// Only send HEAD if we've processed all known atoms
						final boolean include = atomsObservationState.curCount >= observationCountPerAddress.getOrDefault(address, 0L);
						return include ? Observable.just(observation) : Observable.empty();
					} else {
						return Observable.empty();
					}
				})
			);
	}
}
