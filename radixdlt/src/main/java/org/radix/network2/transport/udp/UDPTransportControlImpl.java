package org.radix.network2.transport.udp;

import java.util.concurrent.CompletableFuture;

import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportOutboundConnection;

public class UDPTransportControlImpl implements TransportControl {
	@Override
	public CompletableFuture<TransportOutboundConnection> open(Peer peer) {
		return CompletableFuture.supplyAsync(() -> new UDPTransportOutboundConnection(peer));
	}
}
