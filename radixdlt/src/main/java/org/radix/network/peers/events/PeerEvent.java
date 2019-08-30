package org.radix.network.peers.events;

import org.radix.events.Event;
import org.radix.network2.addressbook.Peer;

public abstract class PeerEvent extends Event 
{
	private final Peer peer;
	
	public PeerEvent(Peer peer) 
	{
		super();
		
		this.peer = peer;
	}

	public Peer getPeer()
	{ 
		return peer; 
	}
	
	@Override
	public String toString()
	{
		return super.toString()+" "+peer.toString();
	}
}
