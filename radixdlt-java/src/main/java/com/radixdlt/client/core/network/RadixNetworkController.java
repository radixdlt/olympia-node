package com.radixdlt.client.core.network;

import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.atoms.AtomObservation;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.network.actions.FetchAtomsAction;
import com.radixdlt.client.core.network.actions.FetchAtomsCancelAction;
import com.radixdlt.client.core.network.actions.FetchAtomsRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.FetchAtomsObservationAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.reducers.RadixNetwork;
import com.radixdlt.client.core.util.TestUtils;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.radix.serialization2.DsonOutput;
import org.radix.serialization2.client.Serialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The meat and bones of the Networking module. This module connects all the epics and reducers
 * to produce a stream of actions and states.
 */
public class RadixNetworkController implements AtomSubmitter {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetworkController.class);

	public static class RadixNetworkControllerBuilder {
		private RadixNetwork network;
		private List<RadixNetworkEpic> epics = new ArrayList<>();

		public RadixNetworkControllerBuilder() {
		}

		public RadixNetworkControllerBuilder setNetwork(RadixNetwork network) {
			this.network = network;
			return this;
		}

		public RadixNetworkControllerBuilder addEpic(RadixNetworkEpic epic) {
			this.epics.add(epic);
			return this;
		}

		public RadixNetworkController build() {
			if (network == null) {
				network = new RadixNetwork();
			}

			return new RadixNetworkController(network, epics);
		}
	}

	private final BehaviorSubject<RadixNetworkState> networkState;

	private final Subject<RadixNodeAction> nodeActions = PublishSubject.<RadixNodeAction>create().toSerialized();

	private RadixNetworkController(RadixNetwork network, List<RadixNetworkEpic> epics) {
		Objects.requireNonNull(network);
		Objects.requireNonNull(epics);

		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));

		// Run reducers first
		ConnectableObservable<RadixNodeAction> reducedNodeActions = nodeActions.doOnNext(action -> {
			LOGGER.info("NEXT ACTION: " + action.toString());

			final RadixNetworkState curState = networkState.getValue();
			RadixNetworkState nextState = network.reduce(curState, action);

			// TODO: also add equals check
			if (nextState != curState) {
				networkState.onNext(nextState);
			}

			// TODO: turn this into an action/state pair so synchronized
		}).publish();

		// Then run Epics
		Set<Observable<RadixNodeAction>> updates = epics.stream()
			.map(epic -> epic.epic(reducedNodeActions, networkState))
			.collect(Collectors.toSet());

		// FIXME: Cleanup disposable
		Observable.merge(updates).subscribe(this::dispatch);

		reducedNodeActions.connect();
	}

	public Observable<RadixNetworkState> getNetwork() {
		return networkState;
	}

	public Observable<RadixNodeAction> getActions() {
		return nodeActions;
	}

	public void dispatch(RadixNodeAction action) {
		nodeActions.onNext(action);
	}

	/**
	 * Immediately submits an atom into the ledger without waiting for subscription. The returned
	 * observable is a full replay of the status of the atom, from submission to acceptance by
	 * the network.
	 *
	 * TODO: refactor out
	 *
	 * @param atom atom to submit into the ledger
	 * @return Observable emitting status updates to submission
	 */
	@Override
	public Observable<SubmitAtomAction> submitAtom(Atom atom) {
		SubmitAtomAction initialAction = SubmitAtomRequestAction.newRequest(atom);
		
		Observable<SubmitAtomAction> status =
			nodeActions.ofType(SubmitAtomAction.class)
				.filter(u -> u.getUuid().equals(initialAction.getUuid()))
				.takeUntil(u -> u instanceof SubmitAtomResultAction);
		ConnectableObservable<SubmitAtomAction> replay = status.replay();
		replay.connect();

		this.dispatch(initialAction);

		return replay;
	}

	public Observable<AtomObservation> fetchAtoms(RadixAddress address) {
		return Observable.create(emitter -> {
			FetchAtomsAction initialAction = FetchAtomsRequestAction.newRequest(address);

			Disposable d = nodeActions.ofType(FetchAtomsObservationAction.class)
				.filter(a -> a.getUuid().equals(initialAction.getUuid()))
				.map(FetchAtomsObservationAction::getObservation)
				.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);

			emitter.setCancellable(() -> {
				d.dispose();
				this.dispatch(FetchAtomsCancelAction.of(initialAction.getUuid(), initialAction.getAddress()));
			});

			this.dispatch(initialAction);
		});
	}
}
