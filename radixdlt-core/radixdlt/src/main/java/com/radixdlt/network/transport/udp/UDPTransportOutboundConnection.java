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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;
import io.netty.buffer.ByteBuf;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;

// FIXME: Dependency on PublicInetAddress singleton
final class UDPTransportOutboundConnection implements TransportOutboundConnection {
	private final InetSocketAddress remoteAddr;
	private final DatagramChannel channel;
	private final NatHandler inetAddress;

	UDPTransportOutboundConnection(DatagramChannel channel, TransportMetadata metadata, NatHandler inetAddress) {
		this.remoteAddr = new InetSocketAddress(
			metadata.get(UDPConstants.METADATA_HOST),
			Integer.valueOf(metadata.get(UDPConstants.METADATA_PORT))
		);
		this.channel = channel;
		this.inetAddress = inetAddress;
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
		InetAddress remoteAddress = remoteAddr.getAddress();

		int totalSize = data.length + inetAddress.computeExtraSize(remoteAddress);
		if (totalSize > UDPConstants.MAX_PACKET_LENGTH) {
			cfsr.complete(SendResult.failure(
				new IOException("Datagram packet to " + remoteAddr + " of size " + totalSize + " is too large")
			));
		} else {
			ByteBuf buffer = this.channel.alloc().directBuffer(totalSize);
			inetAddress.writeExtraData(buffer, remoteAddress);
			buffer.writeBytes(data);

			DatagramPacket msg = new DatagramPacket(buffer, remoteAddr);
			this.channel.writeAndFlush(msg).addListener(f -> {
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

	@Override
	public String toString() {
		return String.format("%s:%s:%s", UDPConstants.NAME, remoteAddr.getHostString(), remoteAddr.getPort());
	}
}
