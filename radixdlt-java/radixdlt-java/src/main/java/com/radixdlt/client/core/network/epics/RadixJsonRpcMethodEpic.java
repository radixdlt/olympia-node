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

package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.actions.GetLivePeersRequestAction;
import com.radixdlt.client.core.network.actions.GetLivePeersResultAction;
import com.radixdlt.client.core.network.actions.GetNodeDataRequestAction;
import com.radixdlt.client.core.network.actions.GetNodeDataResultAction;
import com.radixdlt.client.core.network.actions.GetUniverseRequestAction;
import com.radixdlt.client.core.network.actions.GetUniverseResponseAction;
import com.radixdlt.client.core.network.actions.JsonRpcMethodAction;
import com.radixdlt.client.core.network.actions.JsonRpcResultAction;
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
	private final BiFunction<RadixJsonRpcClient, T, Single<JsonRpcResultAction<?>>> methodCall;
	private final Class<T> methodClass;

	public RadixJsonRpcMethodEpic(
		WebSockets webSockets,
		BiFunction<RadixJsonRpcClient, T, Single<JsonRpcResultAction<?>>> methodCall,
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
