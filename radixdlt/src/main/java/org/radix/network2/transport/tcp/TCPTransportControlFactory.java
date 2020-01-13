package org.radix.network2.transport.tcp;

interface TCPTransportControlFactory {

	TCPTransportControl create(TCPConfiguration config, TCPTransportOutboundConnectionFactory outboundFactory, NettyTCPTransportImpl transport);

}
