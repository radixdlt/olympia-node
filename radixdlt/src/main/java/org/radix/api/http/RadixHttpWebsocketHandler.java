package org.radix.api.http;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import com.radixdlt.serialization.Serialization;
import org.radix.api.AtomSchemas;
import org.radix.api.jsonrpc.RadixJsonRpcPeer;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

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
    private static final Logger logger = Logging.getLogger("api");
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
        Serialization serialization) {
        this.radixHttpServer = radixHttpServer;
        this.jsonRpcServer = jsonRpcServer;
        this.peers = peers;
        this.atomsService = atomsService;
        this.serialization = serialization;
    }

    @Override
    public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
        final RadixJsonRpcPeer peer = new RadixJsonRpcPeer(
            jsonRpcServer, atomsService, AtomSchemas.get(), this.serialization, (p, msg) -> {
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
        });

        peers.put(peer, channel);

        channel.addCloseTask(webSocketChannel -> radixHttpServer.closeAndRemovePeer(peer));
        channel.getReceiveSetter().set(new AbstractReceiveListener() {
            @Override
            protected void onFullTextMessage(WebSocketChannel channel, BufferedTextMessage message) throws IOException {
                peer.onMessage(message);
            }
        });

        channel.resumeReceives();
    }
}
