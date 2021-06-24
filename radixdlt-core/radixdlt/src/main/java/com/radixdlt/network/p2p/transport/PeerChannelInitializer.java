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

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.transport.logging.LogSink;
import com.radixdlt.network.p2p.transport.logging.LoggingHandler;
import com.radixdlt.serialization.Serialization;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.Optional;

public final class PeerChannelInitializer extends ChannelInitializer<SocketChannel> {
	private static final Logger log = LogManager.getLogger();

	private static final int MAX_PACKET_LENGTH = 1024 * 1024;
	private static final int FRAME_HEADER_LENGTH = Integer.BYTES;

	private final P2PConfig config;
	private final int magic;
	private final SystemCounters counters;
	private final Serialization serialization;
	private final SecureRandom secureRandom;
	private final ECKeyOps ecKeyOps;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final PeerControl peerControl;
	private final Optional<RadixNodeUri> uri;

	public PeerChannelInitializer(
		P2PConfig config,
		int magic,
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		PeerControl peerControl,
		Optional<RadixNodeUri> uri
	) {
		this.config = Objects.requireNonNull(config);
		this.magic = magic;
		this.counters = Objects.requireNonNull(counters);
		this.serialization = Objects.requireNonNull(serialization);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.peerControl = Objects.requireNonNull(peerControl);
		this.uri = Objects.requireNonNull(uri);
	}

	@Override
	protected void initChannel(SocketChannel socketChannel) {
		final var channel = new PeerChannel(
			config,
			magic,
			counters,
			serialization,
			secureRandom,
			ecKeyOps,
			peerEventDispatcher,
			peerControl,
			uri,
			socketChannel
		);
		final var config = socketChannel.config();
		config.setReceiveBufferSize(MAX_PACKET_LENGTH);
		config.setSendBufferSize(MAX_PACKET_LENGTH);
		config.setOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
		config.setOption(ChannelOption.SO_BACKLOG, 1024);

		if (log.isDebugEnabled()) {
			socketChannel.pipeline().addLast(new LoggingHandler(LogSink.using(log), false));
		}

		uri.ifPresent(u -> log.trace("Initializing peer channel to {}", u));

		final int packetLength = MAX_PACKET_LENGTH + FRAME_HEADER_LENGTH;
		final int headerLength = FRAME_HEADER_LENGTH;

		// TODO(luk): get rid of length-based framing and extend FrameCodec with
		// capability of reading partial frames and multiple frames from a single data read
		socketChannel.pipeline()
			.addLast("unpack", new LengthFieldBasedFrameDecoder(packetLength, 0, headerLength, 0, headerLength))
			.addLast("bytesDecoder", new ByteArrayDecoder())
			.addLast("handler", channel)
			.addLast("pack", new LengthFieldPrepender(headerLength))
			.addLast("bytesEncoder", new ByteArrayEncoder());
	}
}
