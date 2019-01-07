package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.actions.GetLivePeersAction;
import com.radixdlt.client.core.network.actions.GetNodeDataAction;
import com.radixdlt.client.core.network.actions.JsonRpcAction;
import com.radixdlt.client.core.network.actions.JsonRpcAction.JsonRpcActionType;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class RadixJsonRpcMethodsEpic implements RadixNetworkEpic {
	private final ConcurrentHashMap<RadixNode, WebSocketClient> websockets;
	public RadixJsonRpcMethodsEpic(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		this.websockets = websockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return actions
				.filter(a -> a instanceof JsonRpcAction)
				.map(JsonRpcAction.class::cast)
				.filter(u -> u.getJsonRpcActionType().equals(JsonRpcActionType.REQUEST))
				.flatMapMaybe(u -> {
					final WebSocketClient ws = websockets.get(u.getNode());
					return ws.getState()
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMapMaybe(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							if (u instanceof GetLivePeersAction) {
								return jsonRpcClient.getLivePeers()
									.map(l -> GetLivePeersAction.result(u.getNode(), l))
									.toMaybe();
							}

							if (u instanceof GetNodeDataAction) {
								return jsonRpcClient.getInfo()
									.map(data -> GetNodeDataAction.result(u.getNode(), data))
									.toMaybe();
							}

							return Maybe.<RadixNodeAction>empty();
						});
				});
	}
}
