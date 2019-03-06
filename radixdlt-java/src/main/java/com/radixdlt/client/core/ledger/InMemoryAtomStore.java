package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.AtomObservation.Type;
import com.radixdlt.client.core.atoms.RadixHash;
import io.reactivex.functions.Predicate;
import java.util.HashMap;
import java.util.Map;
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
	 * Store an atom under a given destination
	 * TODO: add synchronization if needed
	 *
	 * @param address address to store under
	 * @param atomObservation the atom to store
	 */
	public void store(RadixAddress address, AtomObservation atomObservation) {
		cache.computeIfAbsent(address, euid -> ReplaySubject.create()).onNext(atomObservation);
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
		// TODO: move atom filter outside of class
		return Observable.fromCallable(ValidAtomFilter::new)
			.flatMap(atomFilter ->
				cache.computeIfAbsent(address, euid -> ReplaySubject.create())
					.filter(new Predicate<AtomObservation>() {
						private final Map<RadixHash, Type> curAtomState = new HashMap<>();

						@Override
						public boolean test(AtomObservation observation) {
							if (observation.getAtom() != null) {
								Type curState = curAtomState.get(observation.getAtom().getHash());
								final boolean shouldUpdate;
								if (curState == null) {
									shouldUpdate = observation.getType() == Type.STORE;
								} else {
									shouldUpdate = observation.getType() != curState;
								}

								if (shouldUpdate) {
									curAtomState.put(observation.getAtom().getHash(), observation.getType());
								}
								return shouldUpdate;
							} else {
								return true;
							}
						}
					})
					.flatMap(atomObservation -> {
						if (atomObservation.isHead()) {
							return Observable.just(atomObservation);
						} else {
							return atomFilter.filter(atomObservation);
						}
					})
			);
	}
}
