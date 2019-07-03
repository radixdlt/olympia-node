package org.radix.network.peers.filters;

import java.util.concurrent.TimeUnit;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.peers.Peer;
import org.radix.properties.RuntimeProperties;
import org.radix.time.NtpService;
import org.radix.time.Timestamps;
import org.radix.universe.system.LocalSystem;

public class TCPPeerFilter extends PeerFilter
{
	private static final Logger log = Logging.getLogger();

	@Override
	public boolean filter(Peer peer)
	{
		try
		{
			if (Modules.get(NtpService.class).getUTCTimeMS() - peer.getTimestamp(Timestamps.DISCONNECTED) < Modules.get(RuntimeProperties.class).get("network.connections.reconnect_interval", TimeUnit.SECONDS.toMillis(60)))
				return true;

			if (peer.getSystem().getShards().intersects(LocalSystem.getInstance().getShards()) == false)
				return true;

			// TODO what is this for? Left overs from some previous implementation?
			if (!Modules.get(RuntimeProperties.class).get("network.connections.out.policy", "any").equals("any"))
				return true;
		}
		catch (Exception ex)
		{
			log.error("Could not process filter on TCPPeerFilter for Peer:"+peer.toString(), ex);
			return true;
		}

		return super.filter(peer);
	}
}
