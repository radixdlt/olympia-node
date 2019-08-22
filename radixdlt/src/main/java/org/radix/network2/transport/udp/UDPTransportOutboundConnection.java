package org.radix.network2.transport.udp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;

// FIXME: Dependency on PublicInetAddress singleton
final class UDPTransportOutboundConnection implements TransportOutboundConnection {
	private final InetSocketAddress remoteAddr;
	private final DatagramChannel channel;

	UDPTransportOutboundConnection(DatagramChannel channel, TransportMetadata metadata) {
		this.remoteAddr = new InetSocketAddress(
			metadata.get(UDPConstants.METADATA_UDP_HOST),
			Integer.valueOf(metadata.get(UDPConstants.METADATA_UDP_PORT))
		);
		this.channel = channel;
	}

	@Override
	public void close() throws IOException {
		// Don't close here, as this will close the channel for everyone.
		// Upstream will close.
	}

	@Override
	public CompletableFuture<SendResult> send(byte[] data) {
		final CompletableFuture<SendResult> cfsr = new CompletableFuture<>();
		// NAT: encode source and dest address to work behind NAT and userland proxies (Docker for Windows/Mac)
		InetAddress sourceAddress = PublicInetAddress.getInstance().get();
		byte[] rawSourceAddress = sourceAddress.getAddress();
		byte[] rawDestAddress = remoteAddr.getAddress().getAddress();

		assert rawSourceAddress.length == 4 || rawSourceAddress.length == 16;
		assert rawDestAddress.length == 4 || rawDestAddress.length == 16;

		int totalSize = data.length + rawSourceAddress.length + rawDestAddress.length + 1;
		if (totalSize > UDPConstants.MAX_PACKET_LENGTH) {
			cfsr.complete(SendResult.failure(new IOException("Datagram packet to " + remoteAddr + " of size " + totalSize + " is too large")));
		} else {
			ByteBuf buffer = this.channel.alloc().directBuffer(totalSize)
				.writeByte(getAddressFormat(rawSourceAddress.length, rawDestAddress.length))
				.writeBytes(rawSourceAddress)
				.writeBytes(rawDestAddress)
				.writeBytes(data);

			DatagramPacket msg = new DatagramPacket(buffer, remoteAddr);
			this.channel.writeAndFlush(msg).addListener(f -> {
				Throwable cause = f.cause();
				if (cause == null) {
					cfsr.complete(SendResult.complete());
				} else if (cause instanceof IOException) {
					cfsr.complete(SendResult.failure((IOException) cause));
				} else if (cause instanceof UncheckedIOException) {
					cfsr.complete(SendResult.failure(((UncheckedIOException) cause).getCause()));
				} else {
					cfsr.complete(SendResult.failure(new IOException(cause)));
				}
			});
		}
		return cfsr;
	}

	private byte getAddressFormat(int srclen, int dstlen) {
		// MSB: switch between old/new protocol format
		return (byte) (0x80 | (srclen != 4 ? 0x02 : 0x00) | (dstlen != 4 ? 0x01 : 0x00));
	}
}
