package com.radixdlt.client.core.ledger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;

/**
 * Module responsible for fetches and merges of new atoms into the Atom Store.
 */
public class RadixAtomPuller implements AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<RadixAddress, Observable<Atom>> cache = new ConcurrentHashMap<>();

	/**
	 * The mechanism by which to fetch atoms
	 */
	private final Function<RadixAddress, Observable<Atom>> fetcher;

	/**
	 * The mechanism by which to merge or store atoms
	 */
	private final BiConsumer<RadixAddress, Atom> atomStore;

	public RadixAtomPuller(Function<RadixAddress, Observable<Atom>> fetcher, BiConsumer<RadixAddress, Atom> atomStore) {
		this.fetcher = fetcher;
		this.atomStore = atomStore;
	}

	@Override
	public Disposable pull(RadixAddress address) {
		return cache.computeIfAbsent(
			address, destination -> {
				Observable<Atom> fetchedAtoms = fetcher.apply(destination)
					.publish().refCount(2);
				fetchedAtoms.subscribe(atom -> atomStore.accept(address, atom));
				return fetchedAtoms;
			}
		).subscribe();
	}
}
