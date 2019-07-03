package org.radix.network.peers.events;

import org.radix.network.peers.Peer;

public final class PeerDisconnectingEvent extends PeerEvent
{
	public PeerDisconnectingEvent(Peer peer)
	{
		super(peer);
	}
}
