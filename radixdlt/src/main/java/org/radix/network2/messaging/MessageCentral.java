package org.radix.network2.messaging;

import java.util.concurrent.CompletableFuture;

import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportException;

public interface MessageCentral {

	/**
	 * Send a single message to a peer.
	 * If required, the messaging system will establish a connection to the peer, or
	 * re-use an existing connection.  This method will block until the message can be
	 * submitted to the underlying transport.
	 *
	 * @param peer The peer to send the message to
	 * @param message The message to send
	 * @return A {@link CompletableFuture} indicating the result of the send
	 */
	CompletableFuture<SendResult> send(Peer peer, Message message) throws TransportException;

	/**
	 * Register a callback to be called when messages of a particular type are recieved.
	 * <p>
	 * Note that messages are dispatched to listeners synchronously within the message
	 * dispatch loop.  Clients should ensure that they do not perform blocking operations
	 * such as I/O or long-running computations in this thread.  If necessary clients can
	 * use the callback to place the received items in a queue or stream for clients to
	 * retrieve and process in a separate thread.
	 *
	 * @param messageType The type of message to be notified of
	 * @param listener The listener to notify
	 * @return {@code true} if the listener was added, {@code false} if the listener is already registered
	 */
	<T extends Message> boolean addListener(Class<T> messageType, MessageListener<T> listener);

	/**
	 * Remove a callback from those to be called when messages of a particular type are recieved.
	 *
	 * @param messageType The type of message for the callback
	 * @param listener The listener to remove
	 * @return {@code true} if the listener was remove, {@code false} if the listener was not registered
	 */
	<T extends Message> boolean removeListener(Class<T> messageType, MessageListener<T> listener);

}
