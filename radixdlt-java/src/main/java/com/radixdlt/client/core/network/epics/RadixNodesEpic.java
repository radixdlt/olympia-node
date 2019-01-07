package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.WebSocketClient;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixNodesEpic implements RadixNetworkEpic {
	private static final Logger LOGGER = LoggerFactory.getLogger(RadixNodesEpic.class);

	/**
	 * Hot observable which updates subscribers of new connection events
	 */
	private final BehaviorSubject<RadixNetworkState> networkState;

	private final ConcurrentHashMap<RadixNode, WebSocketClient> websockets = new ConcurrentHashMap<>();

	public RadixNodesEpic() {
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

		Observable<RadixNodeAction> livePeers = actions
			.filter(a -> a instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.GET_LIVE_PEERS))
			.doOnNext(u -> websockets.get(u.getNode()).connect())
			.flatMap(u -> {
				WebSocketClient ws = websockets.get(u.getNode());
				RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
				return jsonRpcClient.getLivePeers()
					.toObservable()
					.flatMapIterable(p -> p)
					.map(data -> new RadixNode(data.getIp(), u.getNode().isSsl(), u.getNode().getPort()))
					.map(NodeUpdate::add);
			});

		Observable<RadixNodeAction> nodeData = actions
			.filter(a -> a instanceof NodeUpdate)
			.map(NodeUpdate.class::cast)
			.filter(u -> u.getType().equals(NodeUpdateType.GET_NODE_DATA))
			.doOnNext(u -> websockets.get(u.getNode()).connect())
			.flatMapSingle(u -> {
				WebSocketClient ws = websockets.get(u.getNode());
				RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
				return jsonRpcClient.getInfo()
					.map(data -> NodeUpdate.nodeData(u.getNode(), data));
			});

		return Observable.merge(addNodes, connectNodes, livePeers, nodeData);
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
				case ADD_NODE_DATA:
			}
		}
	}
}
