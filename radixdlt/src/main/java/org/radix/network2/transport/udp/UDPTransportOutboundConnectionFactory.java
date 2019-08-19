package org.radix.network2.transport.udp;

import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import io.netty.channel.socket.DatagramChannel;

interface UDPTransportOutboundConnectionFactory {
	TransportOutboundConnection create(DatagramChannel channel, TransportMetadata metadata);
}
