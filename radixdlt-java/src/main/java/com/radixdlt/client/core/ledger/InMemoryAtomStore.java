package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.serialization.Dson;
import io.reactivex.Observable;
import io.reactivex.subjects.ReplaySubject;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of a data store for all atoms in a shard
 */
public class InMemoryAtomStore implements AtomStore {

	/**
	 * The In Memory Atom Data Store
	 */
	private final ConcurrentHashMap<RadixAddress, ReplaySubject<Atom>> cache = new ConcurrentHashMap<>();

	/**
	 * Store an atom under a given destination
	 * TODO: add synchronization if needed
	 *
	 * @param address address to store under
	 * @param atom the atom to store
	 */
	public void store(RadixAddress address, Atom atom) {
		cache.computeIfAbsent(address, euid -> ReplaySubject.create()).onNext(atom);
	}

	/**
	 * Returns an unending stream of validated atoms which are stored at a particular destination.
	 *
	 * @param address address (which determines shard) to query atoms for
	 * @return an Atom Observable
	 */
	public Observable<Atom> getAtoms(RadixAddress address) {
		Objects.requireNonNull(address);
		// TODO: move atom filter outside of class
		return Observable.fromCallable(() -> new ValidAtomFilter(address, Dson.getInstance()))
			.flatMap(atomFilter ->
				cache.computeIfAbsent(address, euid -> ReplaySubject.create())
					.distinct()
					.flatMap(atomFilter::filter)
			);
	}
}
