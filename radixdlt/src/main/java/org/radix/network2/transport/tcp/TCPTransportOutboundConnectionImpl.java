package org.radix.network2.transport.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

// FIXME: Dependency on PublicInetAddress singleton
final class TCPTransportOutboundConnectionImpl implements TransportOutboundConnection {
	private final InetSocketAddress remoteAddr;
	private final Channel channel;

	TCPTransportOutboundConnectionImpl(Channel channel, TransportMetadata metadata) {
		// Note that this isn't necessarily the actual metadata we are connected to,
		// as we may be reusing an inbound connection.
		this.remoteAddr = new InetSocketAddress(
			metadata.get(TCPConstants.METADATA_TCP_HOST),
			Integer.valueOf(metadata.get(TCPConstants.METADATA_TCP_PORT))
		);
		this.channel = channel;
	}

	@Override
	public void close() throws IOException {
		try {
			this.channel.close().sync();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted", e);
		}
	}

	@Override
	public CompletableFuture<SendResult> send(byte[] data) {
		final CompletableFuture<SendResult> cfsr = new CompletableFuture<>();

		int dataLength = data.length;
		if (dataLength > TCPConstants.MAX_PACKET_LENGTH) {
			cfsr.complete(SendResult.failure(new IOException("TCP packet to " + remoteAddr + " of size " + dataLength + " is too large")));
		} else {
			ByteBuf buffer = this.channel.alloc().directBuffer(dataLength).writeBytes(data);

			this.channel.writeAndFlush(buffer).addListener(f -> {
				Throwable cause = f.cause();
				if (cause == null) {
					cfsr.complete(SendResult.complete());
				} else {
					cfsr.complete(SendResult.failure(cause));
				}
			});
		}
		return cfsr;
	}
}
