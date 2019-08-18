package org.radix.network2.messaging;

import org.radix.network.messaging.Message;
import org.radix.network.peers.Peer;

/**
 * A listener to messages of a specific type.
 *
 * @param <T> The type of the message to listen for.
 */
@FunctionalInterface
public interface MessageListener<T extends Message> {
	void handleMessage(Peer source, T message);
}
