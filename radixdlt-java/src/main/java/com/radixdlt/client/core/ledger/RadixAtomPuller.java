package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import io.reactivex.Observable;
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

	/**
	 * Fetches atoms and pushes them into the atom store. Multiple pulls on the same address
	 * will return a disposable to the same observable. As long as there is one subscriber to an
	 * address this will continue fetching and storing atoms.
	 *
	 * @param address shard address to get atoms from
	 * @return disposable to dispose to stop fetching
	 */
	@Override
	public Observable<AtomObservation> pull(RadixAddress address) {
		return cache.computeIfAbsent(
			address,
			destination -> fetcher.apply(destination)
				.doOnNext(atomObservation -> atomStore.accept(address, atomObservation))
				.publish()
				.refCount()
		);
	}
}
