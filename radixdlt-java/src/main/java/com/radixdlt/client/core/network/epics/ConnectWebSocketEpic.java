package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import io.reactivex.Observable;

/**
 * Epic which begins a websocket connection when a ConnectWebSocketAction is seen.
 */
public class ConnectWebSocketEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public ConnectWebSocketEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			actions
				.filter(a -> a instanceof ConnectWebSocketAction)
				.doOnNext(u -> webSockets.get(u.getNode()).connect())
				.ignoreElements()
				.toObservable();
	}
}
