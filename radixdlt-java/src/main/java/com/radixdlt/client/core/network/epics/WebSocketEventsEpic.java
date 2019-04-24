package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.WebSocketEvent;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import io.reactivex.Observable;

/**
 * Epic which emits websocket events from each node
 */
public final class WebSocketEventsEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;

	public WebSocketEventsEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		// TODO: Should this really be an epic?
		return webSockets.getNewNodes()
			.flatMap(n -> webSockets.getOrCreate(n)
						.getState()
						.map(s -> WebSocketEvent.nodeStatus(n, s))
			);
	}
}
