package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.radixdlt.client.core.network.reducers.RadixNetwork;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The meat and bones of the Networking module. This module connects all the epics and reducers
 * to produce a stream of actions and states.
 */
public class RadixNetworkController {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetworkController.class);

	public static class RadixNetworkControllerBuilder {
		private RadixNetwork network;
		private final ImmutableList.Builder<RadixNetworkEpic> epics = new Builder<>();
		private final ImmutableList.Builder<Consumer<RadixNodeAction>> reducers = new Builder<>();

		public RadixNetworkControllerBuilder() {
		}

		public RadixNetworkControllerBuilder addReducer(Consumer<RadixNodeAction> reducer) {
			this.reducers.add(reducer);
			return this;
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

			return new RadixNetworkController(network, epics.build(), reducers.build());
		}
	}

	private final BehaviorSubject<RadixNetworkState> networkState;
	private final Subject<RadixNodeAction> nodeActions = PublishSubject.<RadixNodeAction>create().toSerialized();
	private final Observable<RadixNodeAction> reducedNodeActions;

	// TODO: Move this into a proper reducer framework

	private RadixNetworkController(
		RadixNetwork network,
		ImmutableList<RadixNetworkEpic> epics,
		ImmutableList<Consumer<RadixNodeAction>> reducers
	) {
		Objects.requireNonNull(network);
		Objects.requireNonNull(epics);
		Objects.requireNonNull(reducers);

		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));

		// Run reducers first
		final ConnectableObservable<RadixNodeAction> connectableReducedNodeActions = nodeActions.doOnNext(action -> {

			final RadixNetworkState curState = networkState.getValue();
			RadixNetworkState nextState = network.reduce(curState, action);
			// TODO: Move this into a proper reducer framework
			reducers.forEach(r -> r.accept(action));

			LOGGER.debug("{}", action);

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
}
