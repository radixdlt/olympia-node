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
