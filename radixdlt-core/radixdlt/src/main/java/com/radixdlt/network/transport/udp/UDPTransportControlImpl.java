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
import java.util.concurrent.CompletableFuture;

import com.radixdlt.network.transport.TransportControl;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;

import io.netty.channel.socket.DatagramChannel;

/**
 * A {@link TransportControl} interface for UDP transport.
 * Note that UDP is connectionless, and therefore does not require
 * anything to be done on a per-connection basis.
 */
final class UDPTransportControlImpl implements TransportControl {

	private final DatagramChannel channel;
	private final UDPTransportOutboundConnectionFactory outboundFactory;
	private final NatHandler natHandler;

	UDPTransportControlImpl(
		DatagramChannel channel,
		UDPTransportOutboundConnectionFactory outboundFactory,
		NatHandler natHandler
	) {
		this.channel = channel;
		this.outboundFactory = outboundFactory;
		this.natHandler = natHandler;
	}

	@Override
	public CompletableFuture<TransportOutboundConnection> open(TransportMetadata endpointMetadata) {
		// Note that this only works because UDP "connections" are actually connectionless and use
		// a single shared DatagramSocket to communicate.  Don't try this with TCP, you need to
		// remember the connections and close them at some point.
		return CompletableFuture.completedFuture(outboundFactory.create(channel, endpointMetadata, natHandler));
	}

	@Override
	public void close() throws IOException {
		// Nothing to do here
	}
}
