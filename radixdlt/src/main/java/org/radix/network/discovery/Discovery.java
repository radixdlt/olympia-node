package org.radix.network.discovery;

import java.net.URI;
import java.util.Collection;

import org.radix.network.peers.filters.PeerFilter;

public interface Discovery
{
	public Collection<URI> discover(PeerFilter filter);
}
