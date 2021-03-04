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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;

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
	private static final Logger log = LogManager.getLogger();

	@Sharable
	static class TCPConnectionHandlerChannelInbound extends ChannelInboundHandlerAdapter {
		private final RateLimiter droppedChannelRateLimiter = RateLimiter.create(1.0);
		private final AtomicLong droppedChannelCount = new AtomicLong();

		private final Object lock = new Object();
		private final AtomicInteger channelCount = new AtomicInteger();
		private final Map<String, LinkedList<SocketChannel>> channelMap = Maps.newHashMap();
		private final Map<String, CompletableFuture<TransportOutboundConnection>> pendingMap = Maps.newHashMap();
		private final int maxChannelCount;
		private final SystemCounters counters;

		TCPConnectionHandlerChannelInbound(int maxChannelCount, SystemCounters counters) {
			this.maxChannelCount = maxChannelCount;
			this.counters = counters;
		}

		@VisibleForTesting
		int channelMapSize() {
			return this.channelMap.size();
		}

		@VisibleForTesting
		int pendingMapSize() {
			return this.pendingMap.size();
		}

		@VisibleForTesting
		long droppedChannels() {
			return this.droppedChannelCount.get();
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				this.counters.increment(CounterType.NETWORKING_TCP_OPENED);
				if (this.channelCount.getAndIncrement() < this.maxChannelCount) {
					SocketChannel sch = (SocketChannel) ch;
					InetSocketAddress remote = sch.remoteAddress();

					if (remote == null || remote.getAddress() == null) {
						log.error("Can't resolve channel's remote address. Closing connection.");
						ctx.close();
						return;
					}

					String host = remote.getAddress().getHostAddress();
					int port = remote.getPort();
					synchronized (lock) {
						removePending(formatAddress(host, port));
						addChannel(host, sch);
					}
				} else {
					// Too many channels, we just close and exit.
					// Include rate limited log of total dropped channels.
					long droppedChannels = this.droppedChannelCount.incrementAndGet();
					if (this.droppedChannelRateLimiter.tryAcquire()) {
						log.error("Total of {} channel{} dropped due to connection limit",
							droppedChannels, 1L == droppedChannels ? "" : "s");
					}
					ctx.close();
					return;
				}
			}
			super.channelActive(ctx);
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				this.counters.increment(CounterType.NETWORKING_TCP_CLOSED);
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
			String port = metadata.get(TCPConstants.METADATA_PORT);
			String hostAndPort = formatAddress(host, port);
			synchronized (lock) {
				cleanChannels("Clean");

				CompletableFuture<TransportOutboundConnection> pending = this.pendingMap.get(hostAndPort);
				if (pending != null) {
					log.trace("Reuse pending {}", hostAndPort);
					return pending;
				}

				LinkedList<SocketChannel> items = this.channelMap.get(host);

				final SocketChannel channel;
				if (items != null && !items.isEmpty()) {
					channel = items.stream().filter(SocketChannel::isActive).findFirst().orElse(null);
				} else {
					channel = null;
				}
				if (channel == null) {
					ChannelFuture cf = transport.createChannel(host, Integer.parseInt(port));
					final CompletableFuture<TransportOutboundConnection> cfsr0 = new CompletableFuture<>();
					final CompletableFuture<TransportOutboundConnection> cfsr = cfsr0.whenComplete((obc, t) -> {
						log.trace("Completed");
						if (t != null) {
							// If we are completing exceptionally, then we need to remove the pending connection
							synchronized (lock) {
								removePending(hostAndPort);
							}
						}
					});
					log.trace("Add pending {}", hostAndPort);
					this.pendingMap.put(hostAndPort, cfsr);
					cf.addListener(f -> {
						Throwable cause = f.cause();
						if (cause == null) {
							log.trace("Listener completed");
							cfsr0.complete(outboundFactory.create(cf.channel(), metadata));
						} else {
							log.trace("Listener completed exceptionally");
							cfsr0.completeExceptionally(cause);
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
						.filter(Objects::nonNull)
						.forEachOrdered(futures::add);
					channels.clear();
				}
			}
			futures.forEach(ChannelFuture::syncUninterruptibly);
		}

		// Requires "lock" to be held
		void removePending(String host) {
			if (null != this.pendingMap.remove(host)) {
				log.trace("Remove pending {}", host);
			}
		}

		// Requires "lock" to be held
		private void addChannel(String host, SocketChannel ch) {
			this.channelMap.computeIfAbsent(host, k -> Lists.newLinkedList()).addFirst(ch);
			channelDebug("Add", ch);
		}

		// Requires "lock" to be held
		private void cleanChannels(String where) {
			Iterator<Map.Entry<String, LinkedList<SocketChannel>>> i = this.channelMap.entrySet().iterator();
			while (i.hasNext()) {
				Map.Entry<String, LinkedList<SocketChannel>> entry = i.next();
				Iterator<SocketChannel> j = entry.getValue().iterator();
				while (j.hasNext()) {
					SocketChannel ch = j.next();
					if (!ch.isOpen()) {
						channelDebug(where, ch);
						j.remove();
					}
				}
				if (entry.getValue().isEmpty()) {
					// Remove dangling empty lists from map
					i.remove();
				}
			}
		}

		private void channelDebug(String what, SocketChannel c) {
			if (log.isDebugEnabled()) {
				log.debug("{} channel from {} to {}", what, formatAddress(c.localAddress()), formatAddress(c.remoteAddress()));
			}
		}

		private String formatAddress(InetSocketAddress addr) {
			return formatAddress(addr.getAddress().getHostAddress(), addr.getPort());
		}

		private String formatAddress(String host, String port) {
			return String.format("%s:%s", host, port);
		}

		private String formatAddress(String host, int port) {
			return String.format("%s:%s", host, port);
		}
	}

	private final TCPConnectionHandlerChannelInbound handler;
	private final TCPTransportOutboundConnectionFactory outboundFactory;
	private final NettyTCPTransport transport;

	TCPTransportControlImpl(
		TCPConfiguration config,
		TCPTransportOutboundConnectionFactory outboundFactory,
		NettyTCPTransport transport,
		SystemCounters counters
	) {
		this.outboundFactory = outboundFactory;
		this.transport = transport;
		this.handler = new TCPConnectionHandlerChannelInbound(config.maxChannelCount(1024), counters);
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
