package org.radix.network.peers.events;

import org.radix.network.peers.Peer;

public final class PeerConnectingEvent extends PeerEvent
{
	public PeerConnectingEvent(Peer peer)
	{
		super(peer);
	}
}
