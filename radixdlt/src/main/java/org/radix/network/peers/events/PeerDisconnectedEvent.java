package org.radix.network.peers.events;

import org.radix.network.peers.Peer;

public final class PeerDisconnectedEvent extends PeerEvent
{
	public PeerDisconnectedEvent(Peer peer)
	{
		super(peer);
	}
}
