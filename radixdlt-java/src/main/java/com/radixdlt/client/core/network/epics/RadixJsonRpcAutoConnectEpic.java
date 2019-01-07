package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.JsonRpcAction;
import com.radixdlt.client.core.network.actions.JsonRpcAction.JsonRpcActionType;
import com.radixdlt.client.core.network.websocket.WebSocketClient;
import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import io.reactivex.Observable;
import java.util.concurrent.ConcurrentHashMap;

public class RadixJsonRpcAutoConnectEpic implements RadixNetworkEpic {
	private final ConcurrentHashMap<RadixNode, WebSocketClient> websockets;
	public RadixJsonRpcAutoConnectEpic(ConcurrentHashMap<RadixNode, WebSocketClient> websockets) {
		this.websockets = websockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			actions
				.filter(a -> a instanceof JsonRpcAction)
				.map(JsonRpcAction.class::cast)
				.filter(a -> a.getJsonRpcActionType().equals(JsonRpcActionType.REQUEST))
				.flatMap(a -> {
					final WebSocketClient ws = websockets.get(a.getNode());
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
