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

package com.radixdlt.network.transport.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.messaging.InboundMessageConsumer;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.TransportInfo;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.TooLongFrameException;

// For now we are just going to queue up the messages here.
// In the longer term, we would dispatch the messages in the netty group as well,
// but I think that would be problematic right now, as there is undoubtedly some
// downstream blocking that will happen, and this has the potential to slow down
// the whole inbound network pipeline.
final class TCPNettyMessageHandler extends SimpleChannelInboundHandler<ByteBuf> {
	private static final Logger log = LogManager.getLogger();

	private final InboundMessageConsumer messageSink;

	// Limit rate at which bad SocketAddress type logs are produced
	private final RateLimiter logRateLimiter = RateLimiter.create(1.0);


	TCPNettyMessageHandler(InboundMessageConsumer messageSink) {
		this.messageSink = messageSink;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) throws Exception {
		SocketAddress socketSender = ctx.channel().remoteAddress();
		if (socketSender instanceof InetSocketAddress) {
			InetSocketAddress sender = (InetSocketAddress) socketSender;

			final int length = buf.readableBytes();
			final byte[] data = new byte[length];
			buf.readBytes(data);
			TransportInfo source = TransportInfo.of(
				TCPConstants.NAME,
				StaticTransportMetadata.of(
					TCPConstants.METADATA_HOST, sender.getAddress().getHostAddress(),
					TCPConstants.METADATA_PORT, String.valueOf(sender.getPort())
				)
			);
			messageSink.accept(InboundMessage.of(source, data));
		} else if (logRateLimiter.tryAcquire()) {
			String type = socketSender == null ? null : socketSender.getClass().getName();
			String from = socketSender == null ? null : socketSender.toString();
			log.error("Unknown SocketAddress of type {} from {}", type, from);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		String remoteAddress = formatSocketAddress(ctx.channel().remoteAddress());
		if (cause instanceof TooLongFrameException) {
			// Frame desynchronisation or possible interloper sending random data
			log.info("Dropping desynchronised TCP connection from {}: {}", remoteAddress, cause.getMessage());
			ctx.close();
		} else if (cause instanceof IOException) {
			log.info("IOException while receiving TCP data from {}: {}", remoteAddress, cause.getMessage());
		} else {
			log.error(() -> String.format("While receiving TCP data from %s", remoteAddress), cause);
		}
	}

	private static String formatSocketAddress(SocketAddress addr) {
		if (addr == null) {
			return "<none>";
		}
		if (addr instanceof InetSocketAddress) {
			InetSocketAddress isa = (InetSocketAddress) addr;
			return String.format("%s:%s", isa.getHostString(), isa.getPort());
		}
		return addr.toString();
	}
}