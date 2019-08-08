package org.radix.network2.transport;

import org.radix.network2.transport.udp.UDPTransportControlImpl;
import org.radix.network2.transport.udp.UDPTransportImpl;

/**
 * Repository for mandatory transports.
 */
public final class StandardTransports {

	private StandardTransports() {
		throw new IllegalStateException("Can't construct");
	}

	public static Transport getUDPTransport(TransportMetadata metadata) {
		return new UDPTransportImpl(metadata, new UDPTransportControlImpl());
	}

}
