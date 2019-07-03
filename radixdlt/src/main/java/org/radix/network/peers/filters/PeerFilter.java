package org.radix.network.peers.filters;

import java.net.InetAddress;

import org.radix.Radix;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.Network;
import org.radix.network.peers.Peer;
import org.radix.universe.system.LocalSystem;

public class PeerFilter
{
	private static final Logger log = Logging.getLogger();

	private static PeerFilter instance;

	public synchronized static PeerFilter getInstance()
	{
		if (instance == null)
			instance = new PeerFilter();

		return instance;
	}

	public boolean filter(Peer peer)
	{
		try
		{
			InetAddress address = InetAddress.getByName(peer.getURI().getHost());

			if (Modules.get(Interfaces.class).isSelf(address))
				return true;

			if (!Network.getInstance().isWhitelisted(peer.getURI()))
		        return true;

			if (peer.getSystem() == null)
				return true;

			if (peer.getSystem().getNID().equals(LocalSystem.getInstance().getNID()))
				return true;

			if (peer.getSystem().getProtocolVersion() != 0 && peer.getSystem().getProtocolVersion() < Radix.PROTOCOL_VERSION)
	    		return true;

	    	if (peer.getSystem().getAgentVersion() != 0 && peer.getSystem().getAgentVersion() <= Radix.MAJOR_AGENT_VERSION)
	    		return true;

	    	if (peer.isBanned())
    			return true;

			return false;
		}
		catch (Exception ex)
		{
			log.error("Could not process filter on PeerFilter for Peer:"+peer.toString(), ex);
			return true;
		}
	}
}
