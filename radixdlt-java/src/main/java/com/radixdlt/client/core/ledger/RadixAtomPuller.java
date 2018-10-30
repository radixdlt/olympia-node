package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.radixdlt.client.atommodel.accounts.RadixAddress;

/**
 * Module responsible for fetches and merges of new atoms into the Atom Store.
 */
public class RadixAtomPuller implements AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<RadixAddress, Observable<AtomObservation>> cache = new ConcurrentHashMap<>();

	/**
	 * The mechanism by which to fetch atoms
	 */
	private final Function<RadixAddress, Observable<AtomObservation>> fetcher;

	/**
	 * The mechanism by which to merge or store atoms
	 */
	private final BiConsumer<RadixAddress, AtomObservation> atomStore;

	public RadixAtomPuller(
		Function<RadixAddress, Observable<AtomObservation>> fetcher,
		BiConsumer<RadixAddress, AtomObservation> atomStore
	) {
		this.fetcher = fetcher;
		this.atomStore = atomStore;
	}

	@Override
	public Disposable pull(RadixAddress address) {
		return cache.computeIfAbsent(
			address, destination -> {
				Observable<AtomObservation> fetchedAtoms = fetcher.apply(destination)
					.publish().refCount(2);
				fetchedAtoms.subscribe(atomObservation -> atomStore.accept(address, atomObservation));
				return fetchedAtoms;
			}
		).subscribe();
	}
}
