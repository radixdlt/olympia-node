package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.WebSockets;
import com.radixdlt.client.core.network.actions.JsonRpcMethodAction;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Observable;

/**
 * Epic which attempts to open a websocket when a Json Rpc method onto a given node is dispatched.
 */
public final class RadixJsonRpcAutoConnectEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public RadixJsonRpcAutoConnectEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			actions
				.filter(a -> a instanceof JsonRpcMethodAction)
				.flatMap(a -> {
					final WebSocketClient ws = webSockets.getOrCreate(a.getNode());
					return ws.getState()
						.doOnNext(s -> {
							if (s.equals(WebSocketStatus.DISCONNECTED)) {
								ws.connect();
							}
						})
						.filter(s -> s.equals(WebSocketStatus.CONNECTED))
						.firstOrError()
						.ignoreElement()
						.toObservable();
				});
	}
}
