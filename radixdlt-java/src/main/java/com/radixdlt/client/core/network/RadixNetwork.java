package com.radixdlt.client.core.network;

import com.jakewharton.rx.ReplayingShare;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import io.reactivex.observables.ConnectableObservable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.ReplaySubject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.radix.common.tuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixNetwork implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNetwork.class);

	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final BehaviorSubject<RadixNetworkState> networkState;

	private final ConcurrentHashMap<RadixNode, WebSocketClient> websockets = new ConcurrentHashMap<>();

	public RadixNetwork() {
		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));
	}

	public WebSocketClient getWsChannel(RadixNode peer) {
		return websockets.get(peer);
	}

	/**
	 * Returns a cold observable of network state
	 *
	 * @return a cold observable of network state
	 */
	public Observable<RadixNetworkState> getNetworkState() {
		return this.networkState;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		Observable<RadixNodeAction> addNodes = actions
			.filter(a -> a instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.ADD_NODE))
			.flatMap(u -> {
				if (websockets.containsKey(u.getNode())) {
					return Observable.empty();
				}

				WebSocketClient ws = new WebSocketClient(
					listener -> HttpClients.getSslAllTrustingClient().newWebSocket(u.getNode().getLocation(), listener)
				);
				websockets.put(u.getNode(), ws);

				return ws.getState().map(s -> NodeUpdate.nodeStatus(u.getNode(), s));
			});

		Observable<RadixNodeAction> connectNodes = actions
			.filter(a -> a instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.START_CONNECT))
			.doOnNext(u -> websockets.get(u.getNode()).connect())
			.ignoreElements()
			.toObservable();

		return addNodes.mergeWith(connectNodes);
	}

	public void reduce(RadixNodeAction action) {
		if (action instanceof NodeUpdate) {
			NodeUpdate nodeUpdate = (NodeUpdate) action;
			switch(nodeUpdate.getType()) {
				case ADD_NODE: {
					RadixNetworkState prev = networkState.getValue();
					Map<RadixNode, RadixNodeStatus> newMap = new HashMap<>(prev.getPeers());
					newMap.put(action.getNode(), RadixNodeStatus.DISCONNECTED);
					networkState.onNext(new RadixNetworkState(newMap));
					LOGGER.debug(String.format("Added to peer list: %s", action.getNode().getLocation()));
					break;
				}
				case DISCONNECTED:
				case CONNECTING:
				case CONNECTED:
				case CLOSING:
				case FAILED: {
					RadixNetworkState prev = networkState.getValue();
					Map<RadixNode, RadixNodeStatus> newMap = new HashMap<>(prev.getPeers());
					newMap.put(action.getNode(), RadixNodeStatus.valueOf(nodeUpdate.getType().name()));
					networkState.onNext(new RadixNetworkState(newMap));
					break;
				}
			}
		}
	}
}
