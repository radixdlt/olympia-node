package com.radixdlt.client.core.network.epics;

import com.radixdlt.client.core.network.RadixNetworkEpic;
import com.radixdlt.client.core.network.RadixNetworkState;
import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.actions.JsonRpcMethodAction;
import com.radixdlt.client.core.network.epics.WebSocketsEpic.WebSockets;
import io.reactivex.Observable;
import java.util.concurrent.TimeUnit;

/**
 * Epic which attempts to close a websocket when a Json Rpc method is finished executing.
 * Note that the websocket won't close if there are still listeners.
 */
public class RadixJsonRpcAutoCloseEpic implements RadixNetworkEpic {
	private final WebSockets webSockets;
	public RadixJsonRpcAutoCloseEpic(WebSockets webSockets) {
		this.webSockets = webSockets;
	}

	@Override
	public Observable<RadixNodeAction> epic(Observable<RadixNodeAction> actions, Observable<RadixNetworkState> networkState) {
		return
			actions
				.filter(a -> a instanceof JsonRpcMethodAction)
				.delay(5, TimeUnit.SECONDS)
				.doOnNext(a -> webSockets.get(a.getNode()).close())
				.ignoreElements()
				.toObservable();
	}
}
