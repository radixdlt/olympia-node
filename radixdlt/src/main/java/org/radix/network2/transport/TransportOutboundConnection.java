package org.radix.network2.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface TransportOutboundConnection extends Closeable {

	/**
	 * Sends a message over the connection.
	 * This is a blocking operation and will wait until the message can be accepted for sending.
	 *
	 * @param data The data to send
	 * @return A {@link CompletableFuture} with the result
	 */
	CompletableFuture<SendResult> send(byte[] data);

	/**
	 * Tries to send a message over the connection without blocking.
	 * This is a non-blocking operation.  If the message cannot be submitted without blocking
	 * then this method will return {@code null}.
	 *
	 * @param data The data to send
	 * @return A {@link CompletableFuture} with the result, or {@code null} if the data could not be submitted
	 */
	CompletableFuture<SendResult> trySend(byte[] data);

	/**
	 * Tries to send a message over the connection, blocking temporarily.
	 * This is a temporary blocking operation.  If the message cannot be submitted without blocking
	 * within the specified timeout, then this method will return {@code null}.
	 *
	 * @param data The data to send
	 * @param timeout The length of time to wait for submission to be successful
	 * @return A {@link CompletableFuture} with the result, or {@code null} if the data could not be submitted
	 */
	CompletableFuture<SendResult> trySend(byte[] data, TimeUnit timeout);

	CompletableFuture<SendResult> send(Iterable<byte[]> bytes);

}
