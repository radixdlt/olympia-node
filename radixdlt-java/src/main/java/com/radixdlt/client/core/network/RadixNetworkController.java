package com.radixdlt.client.core.network;

import com.radixdlt.client.core.atoms.Atom;
import com.radixdlt.client.core.ledger.AtomSubmitter;
import com.radixdlt.client.core.network.actions.SubmitAtomAction;
import com.radixdlt.client.core.network.actions.SubmitAtomRequestAction;
import com.radixdlt.client.core.network.actions.SubmitAtomResultAction;
import com.radixdlt.client.core.network.reducers.RadixNetwork;
import io.reactivex.Observable;
import io.reactivex.functions.Cancellable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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

	private final Observable<RadixNodeAction> reducedNodeActions;

	// TODO: Move this into a proper reducer framework
	private final CopyOnWriteArrayList<Consumer<RadixNodeAction>> reducers = new CopyOnWriteArrayList<>();

	private RadixNetworkController(RadixNetwork network, List<RadixNetworkEpic> epics) {
		Objects.requireNonNull(network);
		Objects.requireNonNull(epics);

		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));

		// Run reducers first
		final ConnectableObservable<RadixNodeAction> connectableReducedNodeActions = nodeActions.doOnNext(action -> {

			final RadixNetworkState curState = networkState.getValue();
			RadixNetworkState nextState = network.reduce(curState, action);

			LOGGER.debug("{}", action);

			// TODO: Move this into a proper reducer framework
			reducers.forEach(r -> r.accept(action));

			// TODO: also add equals check
			if (nextState != curState) {
				networkState.onNext(nextState);
			}

			// TODO: turn this into an action/state pair so synchronized
		}).publish();

		// Then run Epics
		Set<Observable<RadixNodeAction>> updates = epics.stream()
			.map(epic -> epic.epic(connectableReducedNodeActions, networkState))
			.collect(Collectors.toSet());

		// FIXME: Cleanup disposable
		Observable.merge(updates).subscribe(this::dispatch);

		this.reducedNodeActions = connectableReducedNodeActions;

		connectableReducedNodeActions.connect();
	}

	// HACK
	// TODO: Move this into a proper reducer framework
	public Cancellable addReducer(Consumer<RadixNodeAction> reducer) {
		this.reducers.add(reducer);
		return () -> this.reducers.remove(reducer);
	}

	public Observable<RadixNetworkState> getNetwork() {
		return networkState;
	}

	/**
	 * Get an observable of all actions which have occurred in the network system.
	 * Actions are only emitted after they have been processed by all reducers.
	 *
	 * @return observable of actions in the system
	 */
	public Observable<RadixNodeAction> getActions() {
		return reducedNodeActions;
	}

	/**
	 * Dispatches an action into the system. That is it will be processed through reducers
	 * and then subsequently epics
	 *
	 * @param action the action to dispatch
	 */
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
			reducedNodeActions.ofType(SubmitAtomAction.class)
				.filter(u -> u.getUuid().equals(initialAction.getUuid()))
				.takeUntil(u -> u instanceof SubmitAtomResultAction);
		ConnectableObservable<SubmitAtomAction> replay = status.replay();
		replay.connect();

		this.dispatch(initialAction);

		return replay;
	}
}
