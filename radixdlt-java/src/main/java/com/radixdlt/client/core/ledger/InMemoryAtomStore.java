package com.radixdlt.client.core.ledger;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.radix.serialization2.client.Serialize;

import com.radixdlt.client.core.address.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;

import io.reactivex.Observable;
import io.reactivex.subjects.ReplaySubject;

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
	@Override
	public Observable<Atom> getAtoms(RadixAddress address) {
		Objects.requireNonNull(address);
		// TODO: move atom filter outside of class
		return Observable.fromCallable(() -> new ValidAtomFilter(address, Serialize.getInstance()))
			.flatMap(atomFilter ->
				cache.computeIfAbsent(address, euid -> ReplaySubject.create())
					.distinct()
					.flatMap(atomFilter::filter)
			);
	}
}
