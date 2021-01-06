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

package com.radixdlt.network.messaging;

import java.io.IOException;

import org.radix.network.messaging.Message;
import org.radix.universe.system.SystemMessage;

import com.radixdlt.network.addressbook.Peer;
import com.radixdlt.network.transport.TransportException;
import com.radixdlt.network.transport.TransportInfo;

/**
 * Central processing facility for inbound and outbound messages.
 */
public interface MessageCentral {

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
	 * Sends a {@link SystemMessage} to a specific transport.
	 * This method can be used to allow a node to introduce itself to another
	 * node, when only the transport information is known.
	 *
	 * @param transportInfo Information about the transport to use for sending
	 * @param message The message to send
	 */
	void sendSystemMessage(TransportInfo transportInfo, SystemMessage message) throws TransportException;

	/**
	 * Injects a message into the processing pipeline as if it had been received from
	 * the specified peer.
	 *
	 * @param source the source the message should appear to come from
	 * @param message the message to inject
	 */
	void inject(TransportInfo source, Message message);

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

    /**
     * Closes this {@code MessageCentral} and releases any system resources associated
     * with it. If it is already closed then invoking this method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

}
