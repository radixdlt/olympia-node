/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.network;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.radixdlt.client.core.network.reducers.RadixNetwork;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The meat and bones of the Networking module. This module connects all the epics and reducers
 * to produce a stream of actions and states.
 */
public class RadixNetworkController {
	private static final Logger LOGGER = LogManager.getLogger(RadixNetworkController.class);

	public static class RadixNetworkControllerBuilder {
		private RadixNetwork network;
		private final ImmutableList.Builder<RadixNetworkEpic> epics = new Builder<>();
		private final ImmutableList.Builder<Consumer<RadixNodeAction>> reducers = new Builder<>();
		private final Set<RadixNode> initialNodes = new HashSet<>();

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

		public RadixNetworkControllerBuilder addInitialNodes(Set<RadixNode> nodes) {
			this.initialNodes.addAll(nodes);
			return this;
		}

		public RadixNetworkControllerBuilder addInitialNode(RadixNode node) {
			this.initialNodes.add(node);
			return this;
		}

		public RadixNetworkController build() {
			if (network == null) {
				network = new RadixNetwork();
			}

			Map<RadixNode, RadixNodeState> initState = initialNodes.stream().collect(Collectors.toMap(
				n -> n,
				n -> RadixNodeState.of(n, WebSocketStatus.DISCONNECTED)
			));

			return new RadixNetworkController(
				network,
				new RadixNetworkState(initState),
				epics.build(),
				reducers.build()
			);
		}
	}

	private final BehaviorSubject<RadixNetworkState> networkState;
	private final Subject<RadixNodeAction> nodeActions = PublishSubject.<RadixNodeAction>create().toSerialized();
	private final Observable<RadixNodeAction> reducedNodeActions;

	// TODO: Move this into a proper reducer framework

	private RadixNetworkController(
		RadixNetwork network,
		RadixNetworkState initialState,
		ImmutableList<RadixNetworkEpic> epics,
		ImmutableList<Consumer<RadixNodeAction>> reducers
	) {
		Objects.requireNonNull(network);
		Objects.requireNonNull(epics);
		Objects.requireNonNull(reducers);

		this.networkState = BehaviorSubject.createDefault(initialState);

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
		Observable.merge(updates).subscribe(
			this::dispatch,
			e -> {
				LOGGER.error(e.getMessage());
				networkState.onError(e);
			}
		);

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
