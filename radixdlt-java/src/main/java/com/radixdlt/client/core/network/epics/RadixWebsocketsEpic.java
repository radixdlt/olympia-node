package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.HttpClients;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.WebSocketEvent;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.actions.NodeUpdate;
import com.radixdlt.client.core.network.actions.NodeUpdate.NodeUpdateType;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RadixNetwork manages the state of peers and connections.
 */
public final class RadixWebsocketsEpic implements RadixNetworkEpic {

	public static class RadixWebsocketsEpicBuilder {
		private final List<Function<ConcurrentHashMap<RadixNode, WebSocketClient>, RadixNetworkEpic>> websocketEpics = new ArrayList<>();

		public RadixWebsocketsEpicBuilder addWebsocketEpicProvider(
			Function<ConcurrentHashMap<RadixNode, WebSocketClient>, RadixNetworkEpic> websocketEpic) {
			websocketEpics.add(websocketEpic);
			return this;
		}

		public RadixWebsocketsEpic build() {
			websocketEpics.add(RadixWebsocketsEpic::newWebsocketEpic);
			websocketEpics.add(RadixWebsocketsEpic::connectWebsocketEpic);
			return new RadixWebsocketsEpic(new ArrayList<>(websocketEpics));
		}
	}

	private static RadixNetworkEpic newWebsocketEpic(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, networkState) ->
			actions
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

					return ws.getState().map(s -> WebSocketEvent.nodeStatus(u.getNode(), s));
				});
	}

	private static RadixNetworkEpic connectWebsocketEpic(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		return (actions, networkState) ->
			actions
				.filter(a -> a instanceof NodeUpdate)
				.map(NodeUpdate.class::cast)
				.filter(u -> u.getType().equals(NodeUpdateType.WEBSOCKET_CONNECT))
				.doOnNext(u -> websockets.get(u.getNode()).connect())
				.ignoreElements()
				.toObservable();
	}

	private final List<Function<ConcurrentHashMap<RadixNode, WebSocketClient>, RadixNetworkEpic>> websocketEpics;

	private RadixWebsocketsEpic(List<Function<ConcurrentHashMap<RadixNode, WebSocketClient>, RadixNetworkEpic>> websocketEpics) {
		this.websocketEpics = websocketEpics;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final ConcurrentHashMap<RadixNode, WebSocketClient> websockets = new ConcurrentHashMap<>();

		return
			Observable.merge(
				websocketEpics.stream()
					.map(f -> f.apply(websockets).epic(actions, networkState))
					.collect(Collectors.toSet())
			);
	}
}
