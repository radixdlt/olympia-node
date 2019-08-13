package org.radix.network2.transport.udp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportOutboundConnection;

/**
 * A {@link TransportControl} interface for UDP transport.
 * Note that UDP is connectionless, and therefore does not require
 * anything to be done on a per-connection basis.
 */
class UDPTransportControlImpl implements TransportControl {

	private final UDPTransportOutboundConnection outbound;

	UDPTransportControlImpl(UDPTransportOutboundConnection outbound) {
		this.outbound = outbound;
	}

	@Override
	public CompletableFuture<TransportOutboundConnection> open() {
		return CompletableFuture.completedFuture(outbound);
	}

	@Override
	public void close() throws IOException {
		outbound.close();
	}
}
