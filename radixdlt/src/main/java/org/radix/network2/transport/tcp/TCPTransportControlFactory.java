package org.radix.network2.transport.tcp;

interface TCPTransportControlFactory {

	TCPTransportControl create(TCPTransportOutboundConnectionFactory outboundFactory, NettyTCPTransportImpl transport);

}
