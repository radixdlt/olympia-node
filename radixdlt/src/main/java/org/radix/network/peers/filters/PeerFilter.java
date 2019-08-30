package org.radix.network.peers.filters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;

import org.radix.Radix;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.Interfaces;
import org.radix.network.Network;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.TransportInfo;
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
			// If any of the peer's transports are local addresses, bail out
			if (peer.supportedTransports().count() == 0) {
				return true;
			}

			if (peer.supportedTransports().anyMatch(this::isLocalAddress)) {
				return true;
			}

			if (peer.supportedTransports().anyMatch(this::hostNotWhitelisted)) {
				return true;
			}

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


	private boolean hostNotWhitelisted(TransportInfo ti) {
		String host = ti.metadata().get("host");
		if (host != null) {
			if (!Network.getInstance().isWhitelisted(host)) {
				return true;
			}
		}
		return false;
	}

	private boolean isLocalAddress(TransportInfo ti) {
		try {
			String host = ti.metadata().get("host");
			if (host != null) {
				InetAddress address = InetAddress.getByName(host);
				if (Modules.get(Interfaces.class).isSelf(address)) {
					return true;
				}
			}
			return false;
		} catch (IOException e) {
			throw new UncheckedIOException("Error while checking for local address", e);
		}
	}
}
