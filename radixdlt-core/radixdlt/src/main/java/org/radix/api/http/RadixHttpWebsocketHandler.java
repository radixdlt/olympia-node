/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package org.radix.api.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.api.jsonrpc.RadixJsonRpcPeer;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;

import com.radixdlt.serialization.Serialization;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.AbstractReceiveListener;
import io.undertow.websockets.core.BufferedTextMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;

/**
 * A handler for websockets on the Radix HTTP API that establishes and maintains connections and forwards the messages for processing
 */
/*package*/ final class RadixHttpWebsocketHandler implements WebSocketConnectionCallback {
	private static final Logger logger = LogManager.getLogger();

	private final ConcurrentHashMap<RadixJsonRpcPeer, WebSocketChannel> peers;
	private final RadixJsonRpcServer jsonRpcServer;
	private final RadixHttpServer radixHttpServer;
	private final AtomsService atomsService;
	private final Serialization serialization;

	RadixHttpWebsocketHandler(
		RadixHttpServer radixHttpServer,
		RadixJsonRpcServer jsonRpcServer,
		ConcurrentHashMap<RadixJsonRpcPeer, WebSocketChannel> peers,
		AtomsService atomsService,
		Serialization serialization
	) {
		this.radixHttpServer = radixHttpServer;
		this.jsonRpcServer = jsonRpcServer;
		this.peers = peers;
		this.atomsService = atomsService;
		this.serialization = serialization;
	}

	@Override
	public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
		final var peer = createPeer(channel);
		peers.put(peer, channel);

		channel.addCloseTask(webSocketChannel -> radixHttpServer.closeAndRemovePeer(peer));
		channel.getReceiveSetter().set(createListener(peer));
		channel.resumeReceives();
	}

	private RadixJsonRpcPeer createPeer(final WebSocketChannel channel) {
		return new RadixJsonRpcPeer(
			jsonRpcServer, atomsService, this.serialization, (p, msg) -> peerCallback(channel, p, msg)
		);
	}

	private AbstractReceiveListener createListener(final RadixJsonRpcPeer peer) {
		return new AbstractReceiveListener() {
			@Override
			protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
				peer.onMessage(message);
			}
		};
	}

	private void peerCallback(final WebSocketChannel channel, final RadixJsonRpcPeer p, final String msg) {
		if (channel.isOpen()) {
			try {
				WebSockets.sendText(msg, channel, null);
			} catch (Exception e) {
				logger.error("Websocket connection send error: " + e, e);
				radixHttpServer.closeAndRemovePeer(p);
			}
		} else {
			logger.error("Websocket connection no longer open.");
			radixHttpServer.closeAndRemovePeer(p);
		}
	}
}
