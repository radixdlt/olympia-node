package org.radix.network2.addressbook;

import org.radix.network2.transport.StandardTransports;

public final class StandardFilters {

	private StandardFilters() {
		throw new IllegalStateException("Can't construct");
	}

	public PeerPredicate standardFilter() {
		// TODO: Implement
		// Various standard filter things, including:
		// Not myself
		// Is whitelisted
		// Compatible protocol versions
		// Compatible agent versions

		// FIXME: Not correct, just a stub
		return p -> false;
	}

	public PeerPredicate tcpPeerFilter() {
		return standardFilter().and(p -> p.supportsTransport(StandardTransports.TCP.getName()));
	}

	public PeerPredicate udpPeerFilter() {
		return standardFilter().and(p -> p.supportsTransport(StandardTransports.UDP.getName()));
	}

}
