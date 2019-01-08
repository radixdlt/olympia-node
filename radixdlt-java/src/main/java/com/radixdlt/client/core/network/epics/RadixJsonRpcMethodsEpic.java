package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetNodeDataResultAction;
import com.radixdlt.client.core.network.actions.JsonRpcMethodAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import io.reactivex.Maybe;
import io.reactivex.Observable;

/**
 * Epic which executes Json Rpc methods over the websocket as Json Rpc requests come in. The responses
 * are emitted.
 */
public class RadixJsonRpcMethodsEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;
	public RadixJsonRpcMethodsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return actions
				.filter(a -> a instanceof JsonRpcMethodAction)
				.map(JsonRpcMethodAction.class::cast)
				.flatMapMaybe(a -> {
					final WebSocketClient ws = webSockets.get(a.getNode());
					return ws.getState()
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.flatMapMaybe(i -> {
							RadixJsonRpcClient jsonRpcClient = new RadixJsonRpcClient(ws);
							if (a instanceof GetLivePeersRequestAction) {
								return jsonRpcClient.getLivePeers()
									.map(l -> GetLivePeersResultAction.of(a.getNode(), l))
									.toMaybe();
							}

							if (a instanceof GetNodeDataRequestAction) {
								return jsonRpcClient.getInfo()
									.map(d -> GetNodeDataResultAction.of(a.getNode(), d))
									.toMaybe();
							}

							return Maybe.<RadixNodeAction>empty();
						});
				});
	}
}
