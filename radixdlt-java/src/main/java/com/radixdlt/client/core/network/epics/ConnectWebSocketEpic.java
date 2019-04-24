package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.CloseWebSocketAction;
import com.radixdlt.client.core.network.actions.ConnectWebSocketAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import io.reactivex.Observable;

/**
 * Epic which begins a websocket connection when a ConnectWebSocketAction is seen.
 */
public final class ConnectWebSocketEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public ConnectWebSocketEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		final Observable<RadixNodeAction> onConnect =
			actions
				.filter(a -> a instanceof ConnectWebSocketAction)
				.doOnNext(u -> webSockets.getOrCreate(u.getNode()).connect())
				.ignoreElements()
				.toObservable();

		final Observable<RadixNodeAction> onClose =
			actions
				.filter(a -> a instanceof CloseWebSocketAction)
				.doOnNext(u -> webSockets.getOrCreate(u.getNode()).close())
				.ignoreElements()
				.toObservable();

		return Observable.merge(onConnect, onClose);
	}
}
