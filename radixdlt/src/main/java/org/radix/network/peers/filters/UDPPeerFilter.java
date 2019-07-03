package org.radix.network.peers.filters;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.peers.Peer;
import org.radix.properties.RuntimeProperties;
import org.radix.universe.system.LocalSystem;

public class UDPPeerFilter extends PeerFilter
{
	private static final Logger log = Logging.getLogger();

	@Override
	public boolean filter(Peer peer)
	{
		try
		{
			if (LocalSystem.getInstance().getShards().intersects(peer.getSystem().getShards()) == false)
				return true;

			// TODO what is this for? Left overs from some previous implementation?
			if (!Modules.get(RuntimeProperties.class).get("network.connections.out.policy", "any").equals("any"))
				return true;
		}
		catch (Exception ex)
		{
			log.error("Could not process filter on UDPPeerFilter for Peer:"+peer.toString(), ex);
			return true;
		}

		return super.filter(peer);
	}
}
