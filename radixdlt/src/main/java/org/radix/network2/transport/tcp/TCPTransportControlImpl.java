package org.radix.network2.transport.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;

/**
 * A {@link TransportControl} interface for TCP transport.
 */
final class TCPTransportControlImpl implements TCPTransportControl {

	private static class TCPConnectionHandlerChannelInbound extends ChannelInboundHandlerAdapter {
		private final Object lock = new Object();
		private final Map<String, LinkedList<SocketChannel>> channelMap = Maps.newHashMap();

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				addChannel((SocketChannel) ch);
			}
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			Channel ch = ctx.channel();
			if (ch instanceof SocketChannel) {
				removeChannel((SocketChannel) ch);
			}
		}

		SocketChannel findActiveChannel(String host) {
			synchronized (lock) {
				cleanChannels();
				LinkedList<SocketChannel> items = this.channelMap.get(host);
				if (items != null && !items.isEmpty()) {
					return items.stream().filter(SocketChannel::isActive).findFirst().orElse(null);
				}
			}
			return null;
		}

		void closeAll() {
			final List<ChannelFuture> futures = Lists.newArrayList();
			synchronized (lock) {
				for (LinkedList<SocketChannel> channels : this.channelMap.values()) {
					futures.addAll(channels.stream().map(Channel::close).collect(Collectors.toList()));
					channels.clear();
				}
			}
			futures.forEach(ChannelFuture::syncUninterruptibly);
		}

		private void addChannel(SocketChannel ch) {
			String host = ch.remoteAddress().getAddress().getHostAddress();
			synchronized (lock) {
				this.channelMap.computeIfAbsent(host, k -> Lists.newLinkedList()).addFirst(ch);
			}
		}

		private void removeChannel(SocketChannel ch) {
			InetSocketAddress remoteAddr = ch.remoteAddress();
			String host = remoteAddr.getAddress().getHostAddress();
			synchronized (lock) {
				cleanChannels();
				LinkedList<SocketChannel> items = this.channelMap.get(host);
				if (items != null) {
					items.removeIf(c -> c.remoteAddress().equals(remoteAddr));
					if (items.isEmpty()) {
						// Avoid leaks from empty lists
						this.channelMap.remove(host);
					}
				}
			}
		}

		// Requires "lock" to be held
		private void cleanChannels() {
			for (LinkedList<SocketChannel> channels : this.channelMap.values()) {
				channels.removeIf(ch -> !ch.isOpen());
			}
		}
	}

	private final TCPConnectionHandlerChannelInbound handler = new TCPConnectionHandlerChannelInbound();
	private final TCPTransportOutboundConnectionFactory outboundFactory;
	private final NettyTCPTransport transport;

	TCPTransportControlImpl(TCPTransportOutboundConnectionFactory outboundFactory, NettyTCPTransport transport) {
		this.outboundFactory = outboundFactory;
		this.transport = transport;
	}

	@Override
	public CompletableFuture<TransportOutboundConnection> open(TransportMetadata endpointMetadata) {
		String host = endpointMetadata.get(TCPConstants.METADATA_TCP_HOST);
		SocketChannel channel = this.handler.findActiveChannel(host);
		if (channel == null) {
			int port = Integer.parseInt(endpointMetadata.get(TCPConstants.METADATA_TCP_PORT));
			ChannelFuture cf = this.transport.createChannel(host, port);
			final CompletableFuture<TransportOutboundConnection> cfsr = new CompletableFuture<>();
			cf.addListener(f -> {
				Throwable cause = f.cause();
				if (cause == null) {
					cfsr.complete(this.outboundFactory.create(cf.channel(), endpointMetadata));
				} else {
					cfsr.completeExceptionally(cause);
				}
			});
			return cfsr;
		}
		return CompletableFuture.completedFuture(this.outboundFactory.create(channel, endpointMetadata));
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
