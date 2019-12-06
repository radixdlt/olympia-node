package org.radix.network2.transport.tcp;

import org.radix.network2.transport.Transport;

import io.netty.channel.ChannelFuture;

interface NettyTCPTransport extends Transport {

	ChannelFuture createChannel(String host, int port);

}
