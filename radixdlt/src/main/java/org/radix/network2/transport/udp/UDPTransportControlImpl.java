package org.radix.network2.transport.udp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportOutboundConnection;

public class UDPTransportControlImpl implements TransportControl {

	private final UDPTransportOutboundConnection outbound;

	public UDPTransportControlImpl(UDPTransportOutboundConnection outbound) {
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
