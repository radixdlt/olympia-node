package org.radix.network2.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface TransportOutboundConnection extends Closeable {

	/**
	 * Sends a message over the connection.
	 * This is a blocking operation and will wait until the message can be accepted for sending.
	 *
	 * @param data The data to send
	 * @return A {@link CompletableFuture} with the result
	 */
	CompletableFuture<SendResult> send(byte[] data);

}
