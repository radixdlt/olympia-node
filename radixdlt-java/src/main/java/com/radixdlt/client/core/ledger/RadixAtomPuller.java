package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.identifiers.RadixAddress;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module responsible for fetches and merges of new atoms into the Atom Store.
 */
public class RadixAtomPuller implements AtomPuller {

	/**
	 * Atoms retrieved from the network
	 */
	private final ConcurrentHashMap<RadixAddress, Observable<Object>> cache = new ConcurrentHashMap<>();

	/**
	 * The mechanism by which to fetch atoms
	 */
	private final RadixNetworkController controller;

	public RadixAtomPuller(RadixNetworkController controller) {
		this.controller = controller;
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
	public Observable<Object> pull(RadixAddress address) {
		return cache.computeIfAbsent(
			address,
			addr ->
				Observable.create(emitter -> {
					final FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(addr);

					emitter.setCancellable(() -> {
						controller.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
					});

					controller.dispatch(initialAction);
				})
				.publish()
				.refCount()
		);
	}
}
