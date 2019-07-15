package com.radixdlt.client.core.network;

import com.radixdlt.client.core.network.websocket.WebSocketClient;
import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import java.util.HashMap;
import java.util.Map;

/**
 * All websockets are created and managed here.
 */
public class WebSockets {
	private final Map<RadixNode, WebSocketClient> webSockets = new HashMap<>();
	private final PublishSubject<RadixNode> newNodes = PublishSubject.create();
	private final Object lock = new Object();

	public WebSockets() {
	}

	/**
	 * Returns the unique websocket for a given node. Will never return null.
	 *
	 * @param node a radix node to get the websocket client for
	 * @return a websocket client mapped to the node
	 */
	public WebSocketClient getOrCreate(RadixNode node) {
		final WebSocketClient newClient;
		synchronized (lock)	{
			final WebSocketClient curClient = webSockets.get(node);
			if (curClient != null) {
				return curClient;
			}
			newClient = new WebSocketClient(
				listener -> HttpClients.getSslAllTrustingClient().newWebSocket(node.getWebSocketEndpoint(), listener)
			);
			webSockets.put(node, newClient);
		}
		newNodes.onNext(node);

		return newClient;
	}

	public Observable<RadixNode> getNewNodes() {
		return newNodes;
	}
}
