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

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.utils.Pair;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.network.messaging.InboundMessage;
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

	private final RateLimiter droppedMessagesRateLimiter = RateLimiter.create(1.0);

	private final PublishProcessor<Pair<InetSocketAddress, byte[]>> rawMessageSink = PublishProcessor.create();

	private final RateLimiter logRateLimiter = RateLimiter.create(1.0);

	private final SystemCounters counters;
	private final int bufferSize;

	TCPNettyMessageHandler(SystemCounters counters, int bufferSize) {
		this.counters = counters;
		this.bufferSize = bufferSize;
	}

	Flowable<InboundMessage> inboundMessageRx() {
		return rawMessageSink
			.onBackpressureBuffer(
				this.bufferSize,
				() -> {
					this.counters.increment(CounterType.NETWORKING_TCP_DROPPED_MESSAGES);
					if (droppedMessagesRateLimiter.tryAcquire()) {
						log.warn("TCP msg buffer overflow, dropping msg");
					}
				},
				BackpressureOverflowStrategy.DROP_LATEST)
			.map(this::parseMessage);
	}

	void shutdownRx() {
		rawMessageSink.onComplete();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
		SocketAddress socketSender = ctx.channel().remoteAddress();
		if (socketSender instanceof InetSocketAddress) {
			final InetSocketAddress sender = (InetSocketAddress) socketSender;
			final byte[] data;
			if (buf.isDirect() && buf.nioBuffer().hasArray()) {
				data = buf.nioBuffer().array();
			} else {
				final int length = buf.readableBytes();
				data = new byte[length];
				buf.readBytes(data);
			}
			this.rawMessageSink.onNext(Pair.of(sender, data));
		} else if (logRateLimiter.tryAcquire()) {
			String type = socketSender == null ? null : socketSender.getClass().getName();
			String from = socketSender == null ? null : socketSender.toString();
			log.error("Unknown SocketAddress of type {} from {}", type, from);
		}
	}

	private InboundMessage parseMessage(Pair<InetSocketAddress, byte[]> rawData) {
		final TransportInfo source = TransportInfo.of(
			TCPConstants.NAME,
			StaticTransportMetadata.of(
				TCPConstants.METADATA_HOST, rawData.getFirst().getAddress().getHostAddress(),
				TCPConstants.METADATA_PORT, String.valueOf(rawData.getFirst().getPort())
			)
		);
		return InboundMessage.of(source, rawData.getSecond());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		String remoteAddress = formatSocketAddress(ctx.channel().remoteAddress());
		if (cause instanceof TooLongFrameException) {
			// Frame desynchronisation or possible interloper sending random data
			log.info("Dropping desynchronised TCP connection from {}: {}", remoteAddress, cause.getMessage());
		} else if (cause instanceof IOException) {
			log.info("IOException while receiving TCP data from {}: {}", remoteAddress, cause.getMessage());
		} else {
			log.error(() -> String.format("While receiving TCP data from %s", remoteAddress), cause);
		}
		ctx.close();
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
