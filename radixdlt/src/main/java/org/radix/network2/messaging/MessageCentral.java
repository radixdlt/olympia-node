package org.radix.network2.messaging;

import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.TransportException;

import java.io.Closeable;

/**
 * Central processing facility for inbound and outbound messages.
 */
public interface MessageCentral extends Closeable {

	/**
	 * Sends a single message to a peer.
	 * If required, the messaging system will establish a connection to the peer, or
	 * re-use an existing connection.
	 *
	 * @param peer The peer to send the message to
	 * @param message The message to send
	 */
	void send(Peer peer, Message message) throws TransportException;

	/**
	 * Injects a message into the processing pipeline as if it had been received from
	 * the specified peer.
	 *
	 * @param peer the peer the message should appear to come from
	 * @param message the message to inject
	 */
	void inject(Peer peer, Message message);

	/**
	 * Registers a callback to be called when messages of a particular type are received.
	 * <p>
	 * Note that messages are dispatched to listeners synchronously within the message
	 * dispatch loop.  Clients should ensure that they do not perform blocking operations
	 * such as I/O or long-running computations in this thread.  If necessary clients can
	 * use the callback to place the received items in a queue or stream for clients to
	 * retrieve and process in a separate thread.
	 *
	 * @param messageType The type of message to be notified of
	 * @param listener The listener to notify
	 * @throws IllegalArgumentException if an attempt to add a null listener, or an already registered listener
	 */
	<T extends Message> void addListener(Class<T> messageType, MessageListener<T> listener);

	/**
	 * Removes a callback from those to be called when messages of a particular type are received.
	 *
	 * @param messageType The type of message for the callback
	 * @param listener The listener to remove
	 * @throws IllegalArgumentException if an attempt to remove a null listener
	 */
	<T extends Message> void removeListener(Class<T> messageType, MessageListener<T> listener);

	/**
	 * Removes a callback from those to be called when messages of any type are received.
	 *
	 * @param listener The listener to remove
	 * @throws IllegalArgumentException if an attempt to remove a null listener
	 */
	<T extends Message> void removeListener(MessageListener<T> listener);
}
