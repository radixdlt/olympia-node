package com.radixdlt.client.core.ledger;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.RadixHash;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.Spin;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.radix.common.tuples.Pair;


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
		private final ConcurrentHashMap<RadixHash, Pair<Spin, AtomObservation>> particleSpins = new ConcurrentHashMap<>();
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

		// TODO: Replace the following checks with constraint machine to
		// check for conflicts rather than just DOWN particle conflicts
		private Optional<Atom> getAtomConflict(Atom atom, Particle particle, Spin spin) {
			if (spin != Spin.DOWN) {
				return Optional.empty();
			}

			Pair<Spin, AtomObservation> lastObservation = particleSpins.get(particle.getHash());

			return Optional.ofNullable(lastObservation)
				.flatMap(o -> !o.getSecond().getAtom().equals(atom) && o.getFirst() == Spin.DOWN
						? Optional.of(o.getSecond().getAtom())
						: Optional.empty());
		}

		@Override
		public void accept(RadixNodeAction action) {
			final List<AtomObservation> observations = new ArrayList<>();

			if (action instanceof FetchAtomsObservationAction) {

				FetchAtomsObservationAction fetchAtomsObservationAction = (FetchAtomsObservationAction) action;
				if (fetchAtomsObservationAction.getUuid().equals(initialAction.getUuid())) {

					AtomObservation observation = fetchAtomsObservationAction.getObservation();
					if (observation.hasAtom()) {
						observation.getAtom().spunParticles()
							.map(s -> TransitionedParticle.fromSpunParticle(s, observation.getType()))
							.forEach(t -> {
								final TransitionedParticle tp = (TransitionedParticle) t;
								final Particle particle = tp.getParticle();

								// If a new observed atoms conflicts with a previously soft stored atom,
								// soft stored atom must be deleted
								getAtomConflict(observation.getAtom(), particle, tp.getSpinTo())
									.ifPresent(a -> observations.add(AtomObservation.softDeleted(a)));

								particleSpins.put(particle.getHash(), new Pair<>(tp.getSpinTo(), observation));
							});
					}
					observations.add(observation);
				}
			} else if (action instanceof SubmitAtomResultAction) {

				// Soft storage of atoms so that atoms which are submitted and stored can
				// be immediately used instead of having to wait for fetch atom events.
				final SubmitAtomResultAction submitAtomResultAction = (SubmitAtomResultAction) action;
				final Atom atom = submitAtomResultAction.getAtom();

				if (submitAtomResultAction.getType() == SubmitAtomResultActionType.STORED
					&& atom.addresses().anyMatch(address::equals)
					&& atom.spunParticles().noneMatch(s -> getAtomConflict(atom, s.getParticle(), s.getSpin()).isPresent())
				) {
					final AtomObservation observation = AtomObservation.softStored(atom);
					atom.spunParticles()
						.forEach(s -> particleSpins.put(s.getParticle().getHash(), new Pair<>(s.getSpin(), observation)));

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
			destination ->
				Observable.<AtomObservation>create(emitter -> {
					final FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(address);

					// TODO: Move hack this into a proper reducer framework
					final AtomReducer atomReducer = new AtomReducer(atomStore, initialAction, emitter);
					final Cancellable cancellable = controller.addReducer(atomReducer);

					emitter.setCancellable(() -> {
						cancellable.cancel();
						controller.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
					});

					controller.dispatch(initialAction);
				})
				.publish()
				.refCount()
		);
	}
}
