package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetNodeDataResultAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.JsonRpcMethodAction;
import com.radixdlt.client.core.network.actions.JsonRpcResultAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import com.radixdlt.client.core.network.jsonrpc.RadixJsonRpcClient;
import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.util.function.BiFunction;

/**
 * Epic which executes Json Rpc methods over the websocket as Json Rpc requests come in. The responses
 * are emitted.
 */
public final class RadixJsonRpcMethodEpic<T extends JsonRpcMethodAction> implements RadixNetworkEpic {
	private final WebSockets webSockets;
	private final BiFunction<RadixJsonRpcClient, T, Single<JsonRpcResultAction>> methodCall;
	private final Class<T> methodClass;

	public RadixJsonRpcMethodEpic(
		WebSockets webSockets,
		BiFunction<RadixJsonRpcClient, T, Single<JsonRpcResultAction>> methodCall,
		Class<T> methodClass) {
		this.webSockets = webSockets;
		this.methodCall = methodCall;
		this.methodClass = methodClass;
	}

	private Single<WebSocketClient> waitForConnection(RadixNode node) {
		final WebSocketClient ws = webSockets.getOrCreate(node);
		return ws.getState()
			.doOnNext(s -> {
				if (s.equals(WebSocketStatus.DISCONNECTED)) {
					ws.connect();
				}
			})
			.filter(s -> s.equals(WebSocketStatus.CONNECTED))
			.map(s -> ws)
			.firstOrError();
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return actions
			.ofType(methodClass)
			.flatMapSingle(m ->
				this.waitForConnection(m.getNode())
					.map(RadixJsonRpcClient::new)
					.flatMap(c -> methodCall.apply(c, m))
			);
	}

	public static RadixNetworkEpic createGetLivePeersEpic(WebSockets ws) {
		return new RadixJsonRpcMethodEpic<>(
			ws,
			(client, action) -> client.getLivePeers().map(l -> GetLivePeersResultAction.of(action.getNode(), l)),
			GetLivePeersRequestAction.class
		);
	}

	public static RadixNetworkEpic createGetNodeDataEpic(WebSockets ws) {
		return new RadixJsonRpcMethodEpic<>(
			ws,
			(client, action) -> client.getInfo().map(l -> GetNodeDataResultAction.of(action.getNode(), l)),
			GetNodeDataRequestAction.class
		);
	}

	public static RadixNetworkEpic createGetUniverseEpic(WebSockets ws) {
		return new RadixJsonRpcMethodEpic<>(
			ws,
			(client, action) -> client.universe().map(u -> GetUniverseResponseAction.of(action.getNode(), u)),
			GetUniverseRequestAction.class
		);
	}
}
