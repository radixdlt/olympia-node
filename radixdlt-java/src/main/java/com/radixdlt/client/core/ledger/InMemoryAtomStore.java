package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
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
					.distinct(atomObservation -> {
						// FIXME: make this better
						if (atomObservation.isHead()) {
							return Long.toString(atomObservation.getReceivedTimestamp());
						} else {
							return atomObservation.getAtom().getHash().toString();
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
