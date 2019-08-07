package org.radix.network2.messaging;

import org.radix.containers.BasicContainer;
import org.radix.network2.addressbook.Peer;

@FunctionalInterface
public interface MessageListener<T extends BasicContainer> {
	void handleMessage(Peer source, T message);
}
