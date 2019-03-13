package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomObservation.Type;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.ledger.AtomObservation.AtomObservationUpdateType;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

import io.reactivex.Observable;
import io.reactivex.subjects.ReplaySubject;

/**
 * Implementation of a data store for all atoms in a shard
 */
public class InMemoryAtomStore implements AtomStore {

	/**
	 * The In Memory Atom Data Store
	 */
	private final ConcurrentHashMap<RadixAddress, ReplaySubject<AtomObservation>> cache = new ConcurrentHashMap<>();

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
		cache.computeIfAbsent(address, addr -> ReplaySubject.create()).onNext(atomObservation);
	}

	private static class AtomObservationsState {
		private final HashMap<RadixHash, AtomObservationUpdateType> curAtomState = new HashMap<>();
		private long curCount;

		AtomObservationsState() {
			this.curCount = 0;
		}

		AtomObservationUpdateType get(Atom atom) {
			return curAtomState.get(atom.getHash());
		}

		void put(Atom atom, AtomObservationUpdateType update) {
			curAtomState.put(atom.getHash(), update);
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
			.flatMap(atomsObservationState -> cache.computeIfAbsent(address, addr -> ReplaySubject.create())
				.filter(observation -> {
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

						// Should always update observation state if going from soft to hard observation
						final boolean isSoftToHard = lastUpdate != null && lastUpdate.isSoft() && !nextUpdate.isSoft();

						if (include || isSoftToHard) {
							atomsObservationState.put(observation.getAtom(), nextUpdate);
						}

						return include;
					} else if (observation.isHead()) {
						// Only send HEAD if we've processed all known atoms
						return atomsObservationState.curCount >= observationCountPerAddress.getOrDefault(address, 0L);
					} else {
						return false;
					}
				})
			);
	}
}
