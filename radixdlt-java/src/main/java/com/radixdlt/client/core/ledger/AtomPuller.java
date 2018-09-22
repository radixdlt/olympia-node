package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.atoms.Atom;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<EUID, Observable<Atom>> cache = new ConcurrentHashMap<>();
	private final Function<EUID, Observable<Atom>> fetcher;
	private final BiConsumer<EUID, Atom> atomStore;

	public AtomPuller(Function<EUID, Observable<Atom>> fetcher, BiConsumer<EUID, Atom> atomStore) {
		this.fetcher = fetcher;
		this.atomStore = atomStore;
	}

	public Disposable pull(EUID euid) {
		return cache.computeIfAbsent(
			euid, destination -> {
				Observable<Atom> fetchedAtoms = fetcher.apply(destination)
					.publish().refCount(2);
				fetchedAtoms.subscribe(atom -> atomStore.accept(euid, atom));
				return fetchedAtoms;
			}
		).subscribe();
	}
}
