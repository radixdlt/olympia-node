package org.radix.network2.messaging;

import org.radix.network.messaging.Message;
import org.radix.network2.addressbook.Peer;

@FunctionalInterface
public interface MessageListener<T extends Message> {
	void handleMessage(Peer source, T message);
}
