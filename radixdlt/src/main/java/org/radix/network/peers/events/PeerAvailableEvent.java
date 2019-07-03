package org.radix.network.peers.events;

import org.radix.network.peers.Peer;

public class PeerAvailableEvent extends PeerEvent
{
	public PeerAvailableEvent(Peer peer)
	{
		super(peer);
	}
}