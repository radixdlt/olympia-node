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

import io.reactivex.rxjava3.core.Observable;
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
	 * Returns a Flowable of inbound peer messages of specified type.
	 * @param messageType the message type
	 * @return a Flowable of inbound peer messages
	 */
	<T extends Message> Observable<MessageFromPeer<T>> messagesOf(Class<T> messageType);

    /**
     * Closes this {@code MessageCentral} and releases any system resources associated
     * with it. If it is already closed then invoking this method has no effect.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;
}
