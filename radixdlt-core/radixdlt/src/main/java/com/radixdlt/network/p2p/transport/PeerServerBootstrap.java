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
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.serialization.Serialization;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

@Singleton
public final class PeerServerBootstrap {
	private static final int BACKLOG_SIZE = 100;

	private final P2PConfig config;
	private final SystemCounters counters;
	private final Serialization serialization;
	private final SecureRandom secureRandom;
	private final ECKeyOps ecKeyOps;
	private final ECPublicKey nodeKey;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;

	@Inject
	public PeerServerBootstrap(
		P2PConfig config,
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		@Self ECPublicKey nodeKey,
		EventDispatcher<PeerEvent> peerEventDispatcher
	) {
		this.config = Objects.requireNonNull(config);
		this.counters = Objects.requireNonNull(counters);
		this.serialization = Objects.requireNonNull(serialization);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
		this.nodeKey = Objects.requireNonNull(nodeKey);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
	}

	public void start() throws InterruptedException {
		final var serverGroup = new NioEventLoopGroup(1);
		final var workerGroup = new NioEventLoopGroup();

		final var serverBootstrap = new ServerBootstrap();
		serverBootstrap.group(serverGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.option(ChannelOption.SO_BACKLOG, BACKLOG_SIZE)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.peerConnectionTimeout())
			.option(ChannelOption.TCP_NODELAY, true)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.childHandler(new PeerChannelInitializer(
				config,
				counters,
				serialization,
				secureRandom,
				ecKeyOps,
				nodeKey,
				peerEventDispatcher,
				Optional.empty()
			));

		serverBootstrap.bind(config.listenAddress(), config.listenPort()).sync();
	}
}
