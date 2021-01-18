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
import java.util.concurrent.CompletableFuture;

import com.radixdlt.network.transport.SendResult;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

final class TCPTransportOutboundConnectionImpl implements TransportOutboundConnection {
	private final InetSocketAddress remoteAddr;
	private final Channel channel;

	TCPTransportOutboundConnectionImpl(Channel channel, TransportMetadata metadata) {
		// Note that this isn't necessarily the actual metadata we are connected to,
		// as we may be reusing an inbound connection.
		this.remoteAddr = new InetSocketAddress(
			metadata.get(TCPConstants.METADATA_HOST),
			Integer.valueOf(metadata.get(TCPConstants.METADATA_PORT))
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
			cfsr.complete(
				SendResult.failure(new IOException("TCP packet to " + remoteAddr + " of size " + dataLength + " is too large"))
			);
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

	@Override
	public String toString() {
		return String.format("%s:%s:%s", TCPConstants.NAME, remoteAddr.getHostString(), remoteAddr.getPort());
	}
}
