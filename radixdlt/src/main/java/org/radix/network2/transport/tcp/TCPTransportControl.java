package org.radix.network2.transport.tcp;

import org.radix.network2.transport.TransportControl;

import io.netty.channel.ChannelInboundHandler;

interface TCPTransportControl extends TransportControl {

	// Handler needs to be connected to message loop so that we can
	// be kept informed of new channels that are created.
	ChannelInboundHandler handler();

}
