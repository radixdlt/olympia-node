package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Collections;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixNodesEpic implements RadixNetworkEpic {
	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final BehaviorSubject<RadixNetworkState> networkState;


	public RadixNodesEpic() {
		this.networkState = BehaviorSubject.createDefault(new RadixNetworkState(Collections.emptyMap()));
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
		final ConcurrentHashMap<RadixNode, WebSocketClient> websockets = new ConcurrentHashMap<>();

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

		Observable<RadixNodeAction> jsonRpcEpics = Observable.merge(
			RadixNodeJsonRpcEpics.livePeers(websockets).epic(actions, networkState),
			RadixNodeJsonRpcEpics.nodeData(websockets).epic(actions, networkState),
			RadixNodeJsonRpcEpics.submitAtom(websockets).epic(actions, networkState),
			RadixNodeJsonRpcEpics.fetchAtoms(websockets).epic(actions, networkState)
		);

		return Observable.merge(
			addNodes,
			connectNodes,
			jsonRpcEpics,
			RadixNodeJsonRpcEpics.autoCloseWebsocket(websockets).epic(actions, networkState)
		);
	}

}
