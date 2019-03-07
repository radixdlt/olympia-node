package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;


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
	private final RadixNetworkController controller;

	/**
	 * The mechanism by which to merge or store atoms
	 */
	private final BiConsumer<RadixAddress, AtomObservation> atomStore;

	public RadixAtomPuller(
		RadixNetworkController controller,
		BiConsumer<RadixAddress, AtomObservation> atomStore
	) {
		this.controller = controller;
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
			destination ->
				Observable.<AtomObservation>create(emitter -> {
					FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(address);

					Disposable d = controller.getActions().ofType(FetchAtomsObservationAction.class)
						.filter(a -> a.getUuid().equals(initialAction.getUuid()))
						.map(FetchAtomsObservationAction::getObservation)
						.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);

					emitter.setCancellable(() -> {
						d.dispose();
						controller.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
					});

					controller.dispatch(initialAction);
				})
				.doOnNext(atomObservation -> atomStore.accept(address, atomObservation))
				.publish()
				.refCount()
		);
	}
}
