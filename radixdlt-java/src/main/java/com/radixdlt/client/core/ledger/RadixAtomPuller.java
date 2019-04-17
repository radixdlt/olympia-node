package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.functions.Cancellable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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


	// HACK
	// TODO: Move this into a proper reducer framework
	private static class AtomReducer implements Consumer<RadixNodeAction> {
		private final FetchAtomsAction initialAction;
		private final ObservableEmitter<AtomObservation> emitter;
		private final BiConsumer<RadixAddress, AtomObservation> atomStore;
		private final RadixAddress address;

		AtomReducer(
			BiConsumer<RadixAddress, AtomObservation> atomStore,
			FetchAtomsAction initialAction,
			ObservableEmitter<AtomObservation> emitter
		) {
			this.atomStore = atomStore;
			this.initialAction = initialAction;
			this.address = initialAction.getAddress();
			this.emitter = emitter;
		}

		@Override
		public void accept(RadixNodeAction action) {
			final List<AtomObservation> observations = new ArrayList<>();

			if (action instanceof FetchAtomsObservationAction) {
				FetchAtomsObservationAction fetchAtomsObservationAction = (FetchAtomsObservationAction) action;
				if (fetchAtomsObservationAction.getUuid().equals(initialAction.getUuid())) {
					AtomObservation observation = fetchAtomsObservationAction.getObservation();
					observations.add(observation);
				}
			} else if (action instanceof SubmitAtomResultAction) {

				// Soft storage of atoms so that atoms which are submitted and stored can
				// be immediately used instead of having to wait for fetch atom events.
				final SubmitAtomResultAction submitAtomResultAction = (SubmitAtomResultAction) action;
				final Atom atom = submitAtomResultAction.getAtom();

				if (submitAtomResultAction.getType() == SubmitAtomResultActionType.STORED
					&& atom.addresses().anyMatch(address::equals)
				) {
					final AtomObservation observation = AtomObservation.softStored(atom);
					observations.add(observation);
					observations.add(AtomObservation.head());
				}
			}

			observations.forEach(o -> {
				atomStore.accept(address, o);
				emitter.onNext(o);
			});
		}
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
			addr ->
				Observable.<AtomObservation>create(emitter -> {
					final FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(addr);

					// TODO: Move hack this into a proper reducer framework
					final AtomReducer atomReducer = new AtomReducer(atomStore, initialAction, emitter);
					final Cancellable cancellable = controller.addReducer(atomReducer);

					emitter.setCancellable(() -> {
						cancellable.cancel();
						controller.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
					});

					controller.dispatch(initialAction);
				})
				.doOnSubscribe(d -> atomStore.accept(address, AtomObservation.resync()))
				.publish()
				.refCount()
		);
	}
}
