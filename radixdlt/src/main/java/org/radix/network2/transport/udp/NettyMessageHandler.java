package org.radix.network2.transport.udp;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.messaging.InboundMessage;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

// For now we are just going to queue up the messages here.
// In the longer term, we would dispatch the messages in the netty group as well,
// but I think that would be problematic right now, as there is undoubtedly some
// downstream blocking that will happen, and this has the potential to slow down
// the whole inbound network pipeline.
final class NettyMessageHandler extends SimpleChannelInboundHandler<DatagramPacket> {
	private static final Logger log = Logging.getLogger("transport.udp");

	private final InboundMessageConsumer messageSink;
	private final PublicInetAddress natHandler;


	NettyMessageHandler(PublicInetAddress natHandler, InboundMessageConsumer messageSink) {
		this.messageSink = messageSink;
		this.natHandler = natHandler;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
		final ByteBuf buf = msg.content();
		// part of the NAT address validation process
		if (!natHandler.endValidation(buf)) {
			InetSocketAddress sender = msg.sender();
			InetAddress peerAddress = sender.getAddress();
			natHandler.handleInboundPacket(ctx, peerAddress, buf);

			// NAT validated, just make the message available
			// Clone data and put in queue
			final int length = buf.readableBytes();
			final byte[] data = new byte[length];
			buf.readBytes(data);
			TransportInfo source = TransportInfo.of(
				UDPConstants.UDP_NAME,
				StaticTransportMetadata.of(
					UDPConstants.METADATA_UDP_HOST, sender.getAddress().getHostAddress(),
					UDPConstants.METADATA_UDP_PORT, String.valueOf(sender.getPort())
				)
			);
			messageSink.accept(InboundMessage.of(source, data));
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		log.error("While receiving UDP packet", cause);
	}
}