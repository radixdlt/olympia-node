/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.network.p2p.transport;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.transport.logging.LogSink;
import com.radixdlt.network.p2p.transport.logging.LoggingHandler;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.Serialization;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.bytes.ByteArrayDecoder;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.util.NettyRuntime;
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
	private final Addressing addressing;
	private final int networkId;
	private final SystemCounters counters;
	private final Serialization serialization;
	private final SecureRandom secureRandom;
	private final ECKeyOps ecKeyOps;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final Optional<RadixNodeUri> uri;
	private final ByteBufAllocator byteBufAllocator;

	public PeerChannelInitializer(
		P2PConfig config,
		Addressing addressing,
		int networkId,
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		Optional<RadixNodeUri> uri
	) {
		this.config = Objects.requireNonNull(config);
		this.addressing = Objects.requireNonNull(addressing);
		this.networkId = networkId;
		this.counters = Objects.requireNonNull(counters);
		this.serialization = Objects.requireNonNull(serialization);
		this.secureRandom = Objects.requireNonNull(secureRandom);
		this.ecKeyOps = Objects.requireNonNull(ecKeyOps);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.uri = Objects.requireNonNull(uri);

		final var availProcs = NettyRuntime.availableProcessors();
		this.byteBufAllocator = new PooledByteBufAllocator(
			false,
			availProcs,
			availProcs,
			PooledByteBufAllocator.defaultPageSize(),
			PooledByteBufAllocator.defaultMaxOrder(),
			PooledByteBufAllocator.defaultTinyCacheSize(),
			PooledByteBufAllocator.defaultSmallCacheSize(),
			PooledByteBufAllocator.defaultNormalCacheSize(),
			false
		);
	}

	@Override
	protected void initChannel(SocketChannel socketChannel) {
		counters.increment(CounterType.NETWORKING_P2P_CHANNELS_INITIALIZED);
		final var channel = new PeerChannel(
			config,
			addressing,
			networkId,
			counters,
			serialization,
			secureRandom,
			ecKeyOps,
			peerEventDispatcher,
			uri,
			socketChannel
		);
		final var config = socketChannel.config();
		config.setReceiveBufferSize(MAX_PACKET_LENGTH);
		config.setSendBufferSize(MAX_PACKET_LENGTH);
		config.setOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
		config.setOption(ChannelOption.SO_BACKLOG, 1024);
		config.setAllocator(byteBufAllocator);

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
