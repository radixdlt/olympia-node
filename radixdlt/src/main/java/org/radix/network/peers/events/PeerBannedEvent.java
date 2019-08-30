package org.radix.network.peers.events;

import org.radix.network2.addressbook.Peer;

public final class PeerBannedEvent extends PeerEvent
{
	public PeerBannedEvent(Peer peer)
	{
		super(peer);
	}
}
