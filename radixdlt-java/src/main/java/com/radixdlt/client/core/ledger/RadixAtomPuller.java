package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Module responsible for fetches and merges of new atoms into the Atom Store.
 */
public class RadixAtomPuller implements AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<EUID, Observable<AtomObservation>> cache = new ConcurrentHashMap<>();

	/**
	 * The mechanism by which to fetch atoms
	 */
	private final Function<EUID, Observable<AtomObservation>> fetcher;

	/**
	 * The mechanism by which to merge or store atoms
	 */
	private final BiConsumer<EUID, AtomObservation> atomStore;

	public RadixAtomPuller(Function<EUID, Observable<AtomObservation>> fetcher, BiConsumer<EUID, AtomObservation> atomStore) {
		this.fetcher = fetcher;
		this.atomStore = atomStore;
	}

	public Disposable pull(EUID euid) {
		return cache.computeIfAbsent(
			euid, destination -> {
				Observable<AtomObservation> fetchedAtoms = fetcher.apply(destination)
					.publish().refCount(2);
				fetchedAtoms.subscribe(atomObservation -> atomStore.accept(euid, atomObservation));
				return fetchedAtoms;
			}
		).subscribe();
	}
}
