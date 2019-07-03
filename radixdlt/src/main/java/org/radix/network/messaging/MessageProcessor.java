package org.radix.network.messaging;

import org.radix.network.peers.Peer;

public interface MessageProcessor<T extends Message>
{
	public void process (T message, Peer peer);
}
