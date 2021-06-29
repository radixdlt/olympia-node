/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.network.p2p.transport;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.serialization.Serialization;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

@Singleton
public final class PeerOutboundBootstrapImpl implements PeerOutboundBootstrap {
	private final P2PConfig config;
	private final Addressing addressing;
	private final int networkId;
	private final SystemCounters counters;
	private final Serialization serialization;
	private final SecureRandom secureRandom;
	private final ECKeyOps ecKeyOps;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private Provider<PeerControl> peerControl;

	private final NioEventLoopGroup clientWorkerGroup;

	@Inject
	public PeerOutboundBootstrapImpl(
		P2PConfig config,
		Addressing addressing,
		@NetworkId int networkId,
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		Provider<PeerControl> peerControl
	) {
		this.config = Objects.requireNonNull(config);
		this.addressing = Objects.requireNonNull(addressing);
		this.networkId = networkId;
		this.counters = Objects.requireNonNull(counters);
		this.serialization = Objects.requireNonNull(serialization);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.peerControl = Objects.requireNonNull(peerControl);

		this.clientWorkerGroup = new NioEventLoopGroup();
	}

	@Override
	public void initOutboundConnection(RadixNodeUri uri) {
		final var bootstrap = new Bootstrap();
		bootstrap.group(clientWorkerGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new PeerChannelInitializer(
				config,
				addressing,
				networkId,
				counters,
				serialization,
				secureRandom,
				ecKeyOps,
				peerEventDispatcher,
				peerControl.get(),
				Optional.of(uri)
			))
			.connect(uri.getHost(), uri.getPort());
	}
}
