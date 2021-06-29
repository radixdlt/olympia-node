/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.network.p2p.test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.exception.PublicKeyException;

import com.google.inject.Key;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.transport.PeerChannel;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.serialization.Serialization;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

import java.security.SecureRandom;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

final class MockP2PNetwork {
	private ImmutableList<TestNode> nodes;

	// this needs to be mutable due to circular dependency in runner
	void setNodes(ImmutableList<TestNode> nodes) {
		this.nodes = nodes;
	}

	void createChannel(int clientPeerIndex, RadixNodeUri serverPeerUri) {
		final var clientPeer = nodes.get(clientPeerIndex);
		final var serverPeer = nodes.stream().filter(p -> p.uri.equals(serverPeerUri)).findAny().get();

		final var clientSocketChannel = mock(SocketChannel.class);
		final var serverSocketChannel = mock(SocketChannel.class);

		final var clientChannel = new PeerChannel(
			clientPeer.injector.getInstance(P2PConfig.class),
			Addressing.ofNetwork(Network.LOCALNET),
			1,
			clientPeer.injector.getInstance(SystemCounters.class),
			clientPeer.injector.getInstance(Serialization.class),
			new SecureRandom(),
			ECKeyOps.fromKeyPair(clientPeer.keyPair),
			clientPeer.injector.getInstance(new Key<EventDispatcher<PeerEvent>>() { }),
			clientPeer.injector.getInstance(PeerControl.class),
			Optional.of(serverPeerUri),
			clientSocketChannel
		);

		final var serverChannel = new PeerChannel(
			serverPeer.injector.getInstance(P2PConfig.class),
			Addressing.ofNetwork(Network.LOCALNET),
			1,
			serverPeer.injector.getInstance(SystemCounters.class),
			serverPeer.injector.getInstance(Serialization.class),
			new SecureRandom(),
			ECKeyOps.fromKeyPair(serverPeer.keyPair),
			serverPeer.injector.getInstance(new Key<EventDispatcher<PeerEvent>>() { }),
			clientPeer.injector.getInstance(PeerControl.class),
			Optional.empty(),
			serverSocketChannel
		);

		when(clientSocketChannel.writeAndFlush(any())).thenAnswer(inv -> {
			final var rawData = inv.getArgument(0);
			serverChannel.channelRead0(null, (byte[]) rawData);
			return null;
		});

		when(serverSocketChannel.writeAndFlush(any())).thenAnswer(inv -> {
			final var rawData = inv.getArgument(0);
			clientChannel.channelRead0(null, (byte[]) rawData);
			return null;
		});

		when(clientSocketChannel.close()).thenAnswer(inv -> {
			final var mockChannel = mock(ChannelHandlerContext.class);
			when(mockChannel.channel()).thenReturn(mock(Channel.class));
			clientChannel.channelInactive(mockChannel);
			serverChannel.channelInactive(mockChannel);
			return null;
		});

		when(serverSocketChannel.close()).thenAnswer(inv -> {
			final var mockChannel = mock(ChannelHandlerContext.class);
			when(mockChannel.channel()).thenReturn(mock(Channel.class));
			serverChannel.channelInactive(mockChannel);
			clientChannel.channelInactive(mockChannel);
			return null;
		});

		try {
			serverChannel.channelActive(null);
			clientChannel.channelActive(null);
		} catch (PublicKeyException e) {
			throw new RuntimeException(e);
		}
	}
}
