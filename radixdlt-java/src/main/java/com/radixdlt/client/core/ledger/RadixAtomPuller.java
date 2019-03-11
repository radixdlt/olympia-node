package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Spin;
import com.radixdlt.client.core.network.RadixNetworkController;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction.SubmitAtomResultActionType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
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
					ConcurrentHashMap<RadixHash, Spin> particleSpins = new ConcurrentHashMap<>();

					FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(address);

					Observable<AtomObservation>	fetched = controller.getActions()
						.ofType(FetchAtomsObservationAction.class)
						.filter(a -> a.getUuid().equals(initialAction.getUuid()))
						.doOnNext(a -> {
							AtomObservation observation = a.getObservation();
							if (observation.hasAtom()) {
								observation.getAtom().spunParticles()
									.filter(s -> s.getParticle().getShardables().contains(address))
									.map(s -> TransitionedParticle.fromSpunParticle(s, observation.getType()))
									.forEach(t -> {
										TransitionedParticle tp = (TransitionedParticle) t;
										particleSpins.put(tp.getParticle().getHash(), tp.getSpinTo());
									});
							}
						})
						.map(FetchAtomsObservationAction::getObservation);

					// Soft store can only be used if fetched atoms are from the same node
					// otherwise, deletes can mess up synchronization
					Single<RadixNode> pullNode = controller.getActions()
						.ofType(FetchAtomsObservationAction.class)
						.filter(a -> a.getUuid().equals(initialAction.getUuid()))
						.map(RadixNodeAction::getNode)
						.firstOrError()
						.cache();

					// Soft storage of atoms so that atoms which are submitted and stored can
					// be immediately used instead of having to wait for fetch atom events.
					// TODO: Replace this with constraint machine to check for conflicts
					Observable<AtomObservation> stored = controller.getActions()
						.ofType(SubmitAtomResultAction.class)
						.filter(a -> a.getType() == SubmitAtomResultActionType.STORED)
						.filter(a -> a.getAtom().addresses().anyMatch(address::equals))
						.filter(a -> a.getAtom()
							.spunParticles()
							.noneMatch(s -> s.getSpin() == Spin.DOWN
								&& particleSpins.get(s.getParticle().getHash()) == Spin.DOWN)
						)
						.flatMapMaybe(a -> pullNode.flatMapMaybe(n -> a.getNode().equals(n) ? Maybe.just(a) : Maybe.empty()))
						.map(a -> AtomObservation.softStored(a.getAtom()));

					Disposable d = Observable.merge(fetched, stored)
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
