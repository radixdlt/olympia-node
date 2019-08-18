package org.radix.network2.transport.udp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import io.netty.channel.socket.DatagramChannel;

/**
 * A {@link TransportControl} interface for UDP transport.
 * Note that UDP is connectionless, and therefore does not require
 * anything to be done on a per-connection basis.
 */
final class UDPTransportControlImpl implements TransportControl {

	private final DatagramChannel channel;
	private final UDPTransportOutboundConnectionFactory outboundFactory;

	UDPTransportControlImpl(DatagramChannel channel, UDPTransportOutboundConnectionFactory outboundFactory) {
		this.channel = channel;
		this.outboundFactory = outboundFactory;
	}

	@Override
	public CompletableFuture<TransportOutboundConnection> open(TransportMetadata endpointMetadata) {
		// Note that this only works because UDP "connections" are actually connectionless and use
		// a single shared DatagramSocket to communicate.  Don't try this with TCP, you need to
		// remember the connections and close them at some point.
		return CompletableFuture.completedFuture(outboundFactory.create(channel, endpointMetadata));
	}

	@Override
	public void close() throws IOException {
		// Nothing to do here
	}
}
