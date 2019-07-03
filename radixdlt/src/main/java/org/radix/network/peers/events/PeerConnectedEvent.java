package org.radix.network.peers.events;

import org.radix.network.peers.Peer;

public final class PeerConnectedEvent extends PeerEvent
{
	public PeerConnectedEvent(Peer peer)
	{
		super(peer);
	}
}
