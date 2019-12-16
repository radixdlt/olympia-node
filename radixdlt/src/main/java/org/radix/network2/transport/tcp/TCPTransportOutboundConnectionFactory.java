package org.radix.network2.transport.tcp;

import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import io.netty.channel.Channel;

public interface TCPTransportOutboundConnectionFactory {

	TransportOutboundConnection create(Channel channel, TransportMetadata endpointMetadata);

}
