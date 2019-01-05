package com.radixdlt.client.core.network;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.AtomSubmissionUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate;
import com.radixdlt.client.core.network.actions.AtomsFetchUpdate.AtomsFetchState;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RadixNetworkController implements AtomSubmitter {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetworkController.class);

	public static class RadixNetworkControllerBuilder {
		private RadixNetwork network;
		private RadixUniverseConfig config;
		private List<RadixNetworkEpic> epics = new ArrayList<>();

		public RadixNetworkControllerBuilder() {
		}

		public RadixNetworkControllerBuilder network(RadixNetwork network) {
			this.network = network;
			return this;
		}

		public RadixNetworkControllerBuilder checkUniverse(RadixUniverseConfig config) {
			this.config = config;
			return this;
		}

		public RadixNetworkControllerBuilder addEpic(RadixNetworkEpic epic) {
			this.epics.add(epic);
			return this;
		}


		public RadixNetworkController build() {
			Objects.requireNonNull(network);

			return new RadixNetworkController(network, epics);
		}
	}

	/**
	 * The selector to use to decide between a list of viable peers
	 */
	private final RadixNetwork network;

	private final Subject<RadixNodeAction> nodeActions = PublishSubject.<RadixNodeAction>create().toSerialized();

	private RadixNetworkController(RadixNetwork network, List<RadixNetworkEpic> epics) {
		this.network = network;

		// Run reducers first
		ConnectableObservable<RadixNodeAction> reducedNodeActions = nodeActions.doOnNext(action -> {
			LOGGER.info("NEXT ACTION: " + action.toString());

			if (action instanceof NodeUpdate) {
				network.reduce(action);
			}
			// TODO: turn this into an action/state pair so synchronized
		}).publish();


		// Then run Epics
		Set<Observable<RadixNodeAction>> updates = epics.stream()
			.map(epic -> epic.epic(reducedNodeActions, network.getNetworkState()))
			.collect(Collectors.toSet());
		Observable.merge(updates).subscribe(nodeActions::onNext);

		reducedNodeActions.connect();
	}

	public RadixNetwork getNetwork() {
		return network;
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	@Override
	public Observable<AtomSubmissionUpdate> submitAtom(Atom atom) {
		AtomSubmissionUpdate initialAction = AtomSubmissionUpdate.searchForNode(atom);

		Observable<AtomSubmissionUpdate> status = nodeActions
			.filter(a -> a instanceof AtomSubmissionUpdate)
			.map(AtomSubmissionUpdate.class::cast)
			.filter(u -> u.getUuid().equals(initialAction.getUuid()))
			.takeUntil(AtomSubmissionUpdate::isComplete);
		ConnectableObservable<AtomSubmissionUpdate> replay = status.replay();
		replay.connect();

		nodeActions.onNext(initialAction);

		return replay;
	}

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		return Observable.create(emitter -> {
			AtomsFetchUpdate initialAction = AtomsFetchUpdate.searchForNode(address);

			Disposable d = nodeActions
				.filter(a -> a instanceof AtomsFetchUpdate)
				.map(AtomsFetchUpdate.class::cast)
				.filter(a -> a.getUuid().equals(initialAction.getUuid()))
				.filter(a -> a.getState().equals(AtomsFetchState.ATOM_OBSERVATION))
				.map(AtomsFetchUpdate::getObservation)
				.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);

			emitter.setCancellable(() -> {
				d.dispose();
				nodeActions.onNext(AtomsFetchUpdate.cancel(initialAction.getUuid(), initialAction.getAddress()));
			});

			nodeActions.onNext(initialAction);
		});
	}
}
