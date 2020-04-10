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

package org.radix.network2.transport.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.ChannelHandler.Sharable;

/**
 * A {@link TransportControl} interface for TCP transport.
 * <p>
 * Note that this interface only supports one "application" per IP address.
 * The reasoning behind this is that it is not possible to determine whether
 * an inbound connection (ie those in {@code channelMap}) are for the right
 * "application" on the remote host, so we would therefore need to open an
 * outbound connection regardless.
 */
final class TCPTransportControlImpl implements TCPTransportControl {
	private static final Logger log = LogManager.getLogger("transport.tcp");

	@Sharable
	private static class TCPConnectionHandlerChannelInbound extends ChannelInboundHandlerAdapter {
		private final RateLimiter droppedChannelRateLimiter = RateLimiter.create(1.0);
		private final AtomicLong droppedChannelCount = new AtomicLong();
		private final Object lock = new Object();
		private final AtomicInteger channelCount = new AtomicInteger();
		private final Map<String, LinkedList<SocketChannel>> channelMap = Maps.newHashMap();
		private final int maxChannelCount;

		TCPConnectionHandlerChannelInbound(int maxChannelCount) {
			this.maxChannelCount = maxChannelCount;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				if (this.channelCount.incrementAndGet() < this.maxChannelCount) {
					addChannel((SocketChannel) ch);
				} else {
					// Too many channels, we just close and exit.
					// Include rate limited log of total dropped channels.
					long droppedChannels = this.droppedChannelCount.incrementAndGet();
					if (this.droppedChannelRateLimiter.tryAcquire()) {
						log.error(String.format("Total of %s channel%s dropped due to connection limit",
							droppedChannels, 1L == droppedChannels ? "" : "s"));
					}
					ctx.close();
					return;
				}
			}
			super.channelActive(ctx);
			return;
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				this.channelCount.decrementAndGet();
				synchronized (lock) {
					cleanChannels("Remove");
				}
			}
		}

		CompletableFuture<TransportOutboundConnection> findOrCreateActiveChannel(
			TransportMetadata metadata,
			NettyTCPTransport transport,
			TCPTransportOutboundConnectionFactory outboundFactory
		) {
			String host = metadata.get(TCPConstants.METADATA_HOST);
			synchronized (lock) {
				cleanChannels("Clean");
				LinkedList<SocketChannel> items = this.channelMap.get(host);

				final SocketChannel channel;
				if (items != null && !items.isEmpty()) {
					channel = items.stream().filter(SocketChannel::isActive).findFirst().orElse(null);
				} else {
					channel = null;
				}
				if (channel == null) {
					int port = Integer.parseInt(metadata.get(TCPConstants.METADATA_PORT));
					ChannelFuture cf = transport.createChannel(host, port);
					final CompletableFuture<TransportOutboundConnection> cfsr = new CompletableFuture<>();
					cf.addListener(f -> {
						Throwable cause = f.cause();
						if (cause == null) {
							cfsr.complete(outboundFactory.create(cf.channel(), metadata));
						} else {
							cfsr.completeExceptionally(cause);
						}
					});
					return cfsr;
				}
				return CompletableFuture.completedFuture(outboundFactory.create(channel, metadata));
			}
		}

		void closeAll() {
			final List<ChannelFuture> futures = Lists.newArrayList();
			synchronized (lock) {
				for (LinkedList<SocketChannel> channels : this.channelMap.values()) {
					channels.stream()
						.map(Channel::close)
						.forEachOrdered(futures::add);
					channels.clear();
				}
			}
			futures.forEach(ChannelFuture::syncUninterruptibly);
		}

		private void addChannel(SocketChannel ch) {
			String host = ch.remoteAddress().getAddress().getHostAddress();
			synchronized (lock) {
				this.channelMap.computeIfAbsent(host, k -> Lists.newLinkedList()).addFirst(ch);
				channelInfo("Add", ch);
			}
		}

		// Requires "lock" to be held
		private void cleanChannels(String where) {
			for (Iterator<Map.Entry<String, LinkedList<SocketChannel>>> i = this.channelMap.entrySet().iterator(); i.hasNext(); /* */) {
				Map.Entry<String, LinkedList<SocketChannel>> entry = i.next();
				for (Iterator<SocketChannel> j = entry.getValue().iterator(); j.hasNext(); /* */) {
					SocketChannel ch = j.next();
					if (!ch.isOpen()) {
						channelInfo(where, ch);
						j.remove();
					}
				}
				if (entry.getValue().isEmpty()) {
					// Remove dangling empty lists from map
					i.remove();
				}
			}
		}

		private void channelInfo(String what, SocketChannel c) {
			if (log.isDebugEnabled()) {
				log.debug(String.format("%s: %s channel from %s to %s",
					System.identityHashCode(this), what, formatAddr(c.localAddress()), formatAddr(c.remoteAddress())));
			}
		}

		private String formatAddr(InetSocketAddress addr) {
			return String.format("%s:%s", addr.getAddress().getHostAddress(), addr.getPort());
		}
	}

	private final TCPConnectionHandlerChannelInbound handler;
	private final TCPTransportOutboundConnectionFactory outboundFactory;
	private final NettyTCPTransport transport;

	TCPTransportControlImpl(TCPConfiguration config, TCPTransportOutboundConnectionFactory outboundFactory, NettyTCPTransport transport) {
		this.outboundFactory = outboundFactory;
		this.transport = transport;
		this.handler = new TCPConnectionHandlerChannelInbound(config.maxChannelCount(1024));
	}

	@Override
	public CompletableFuture<TransportOutboundConnection> open(TransportMetadata endpointMetadata) {
		return this.handler.findOrCreateActiveChannel(endpointMetadata, this.transport, this.outboundFactory);
	}

	@Override
	public void close() throws IOException {
		this.handler.closeAll();
	}

	@Override
	public ChannelInboundHandler handler() {
		return this.handler;
	}
}
