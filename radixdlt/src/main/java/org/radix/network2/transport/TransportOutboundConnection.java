package org.radix.network2.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for sending messages on a transport's outbound connections.
 */
public interface TransportOutboundConnection extends Closeable {

	/**
	 * Sends a message over the connection.
	 *
	 * @param data The data to send
	 * @return A {@link CompletableFuture} with the result
	 */
	CompletableFuture<SendResult> send(byte[] data);

}
