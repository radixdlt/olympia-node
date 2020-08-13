/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.transport.udp;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.messaging.InboundMessageConsumer;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

// For now we are just going to queue up the messages here.
// In the longer term, we would dispatch the messages in the netty group as well,
// but I think that would be problematic right now, as there is undoubtedly some
// downstream blocking that will happen, and this has the potential to slow down
// the whole inbound network pipeline.
final class UDPNettyMessageHandler extends SimpleChannelInboundHandler<DatagramPacket> {
	private static final Logger log = LogManager.getLogger();

	private final InboundMessageConsumer messageSink;
	private final NatHandler natHandler;


	UDPNettyMessageHandler(NatHandler natHandler, InboundMessageConsumer messageSink) {
		this.messageSink = messageSink;
		this.natHandler = natHandler;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
		final ByteBuf buf = msg.content();
		// part of the NAT address validation process
		if (!natHandler.endInboundValidation(buf)) {
			InetSocketAddress sender = msg.sender();
			InetAddress peerAddress = sender.getAddress();
			natHandler.handleInboundPacket(ctx, peerAddress, buf);

			// NAT validated, just make the message available
			// Clone data and put in queue
			final int length = buf.readableBytes();
			final byte[] data = new byte[length];
			buf.readBytes(data);
			TransportInfo source = TransportInfo.of(
				UDPConstants.NAME,
				StaticTransportMetadata.of(
					UDPConstants.METADATA_HOST, sender.getAddress().getHostAddress(),
					UDPConstants.METADATA_PORT, String.valueOf(sender.getPort())
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