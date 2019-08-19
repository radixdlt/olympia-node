package org.radix.network2.transport.udp;

import io.netty.channel.socket.DatagramChannel;

@FunctionalInterface
interface UDPTransportControlFactory {

	UDPTransportControlImpl create(DatagramChannel channel, UDPTransportOutboundConnectionFactory outboundFactory);

}
