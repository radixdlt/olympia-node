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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.PeerEvent.PeerConnected;
import com.radixdlt.network.p2p.PeerEvent.PeerDisconnected;
import com.radixdlt.network.p2p.PeerEvent.PeerHandshakeFailed;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult.AuthHandshakeError;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult.AuthHandshakeSuccess;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshaker;
import com.radixdlt.networks.Addressing;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.RateCalculator;
import com.radixdlt.utils.functional.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;

import static com.radixdlt.errors.RadixErrors.ERROR_IO;

/**
 * Class that manages TCP connection channel.
 * It takes care of the initial handshake,
 * creating the frame and message codec
 * and forwarding the messages to MessageCentral.
 */
public final class PeerChannel extends SimpleChannelInboundHandler<byte[]> {
	private static final Logger log = LogManager.getLogger();

	enum ChannelState {
		INACTIVE, AUTH_HANDSHAKE, ACTIVE
	}

	private final Object lock = new Object();
	@SuppressWarnings("UnstableApiUsage")
	private final RateLimiter droppedMessagesRateLimiter = RateLimiter.create(1.0);
	private final PublishProcessor<InboundMessage> inboundMessageSink = PublishProcessor.create();
	private final Flowable<InboundMessage> inboundMessages;

	private final SystemCounters counters;
	private final Addressing addressing;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final Optional<RadixNodeUri> uri;
	private final AuthHandshaker authHandshaker;
	private final boolean isInitiator;
	private final Channel nettyChannel;

	private ChannelState state = ChannelState.INACTIVE;
	private NodeId remoteNodeId;
	private FrameCodec frameCodec;

	private final RateCalculator outMessagesStats = new RateCalculator(Duration.ofSeconds(10), 128);

	public PeerChannel(
		P2PConfig config,
		Addressing addressing,
		int networkId,
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		Optional<RadixNodeUri> uri,
		SocketChannel nettyChannel
	) {
		this.counters = Objects.requireNonNull(counters);
		this.addressing = Objects.requireNonNull(addressing);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.uri = Objects.requireNonNull(uri);
		uri.ifPresent(u -> this.remoteNodeId = u.getNodeId());
		this.authHandshaker = new AuthHandshaker(serialization, secureRandom, ecKeyOps, networkId);
		this.nettyChannel = Objects.requireNonNull(nettyChannel);

		this.isInitiator = uri.isPresent();

		this.inboundMessages = inboundMessageSink
			.onBackpressureBuffer(
				config.channelBufferSize(),
				() -> {
					this.counters.increment(SystemCounters.CounterType.NETWORKING_TCP_DROPPED_MESSAGES);
					final var logLevel = droppedMessagesRateLimiter.tryAcquire() ? Level.WARN : Level.TRACE;
					log.log(logLevel, "TCP msg buffer overflow, dropping msg on {}", this.toString());
				},
				BackpressureOverflowStrategy.DROP_LATEST);
	}

	private void initHandshake(NodeId remoteNodeId) {
		final var initiatePacket = authHandshaker.initiate(remoteNodeId.getPublicKey());
		log.trace("Sending auth initiate to {}", this.toString());
		this.write(initiatePacket);
	}

	public Flowable<InboundMessage> inboundMessages() {
		return inboundMessages;
	}

	private void handleHandshakeData(byte[] data) throws IOException {
		if (this.isInitiator) {
			log.trace("Auth response from {}", this.toString());
			final var handshakeResult = this.authHandshaker.handleResponseMessage(data);
			this.finalizeHandshake(handshakeResult);
		} else {
			log.trace("Auth initiate from {}", this.toString());
			final var result = this.authHandshaker.handleInitialMessage(data);
			this.write(result.getFirst());
			this.finalizeHandshake(result.getSecond());
		}
	}

	private void finalizeHandshake(AuthHandshakeResult handshakeResult) {
		if (handshakeResult instanceof AuthHandshakeSuccess) {
			final var successResult = (AuthHandshakeSuccess) handshakeResult;
			this.remoteNodeId = successResult.getRemoteNodeId();
			this.frameCodec = new FrameCodec(successResult.getSecrets());
			this.state = ChannelState.ACTIVE;
			log.trace("Successful auth handshake: {}", this.toString());
			peerEventDispatcher.dispatch(PeerConnected.create(this));
		} else {
			final var errorResult = (AuthHandshakeError) handshakeResult;
			log.warn("Auth handshake failed on {}: {}", this.toString(), errorResult.getMsg());
			peerEventDispatcher.dispatch(PeerHandshakeFailed.create(this));
			this.disconnect();
		}
	}

	private void handleMessage(byte[] buf) throws IOException {
		synchronized (this.lock) {
			final var maybeFrame = this.frameCodec.tryReadSingleFrame(buf);
			maybeFrame.ifPresentOrElse(
				frame -> inboundMessageSink.onNext(InboundMessage.of(remoteNodeId, frame)),
				() -> log.error("Failed to read a complete frame: {}", this.toString())
			);
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws PublicKeyException {
		this.state = ChannelState.AUTH_HANDSHAKE;

		log.trace("Active: {}", this.toString());

		if (this.isInitiator) {
			this.initHandshake(this.remoteNodeId);
		}
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, byte[] buf) throws Exception {
		switch (this.state) {
			case INACTIVE:
				throw new RuntimeException("Unexpected read on inactive channel");
			case AUTH_HANDSHAKE:
				this.handleHandshakeData(buf);
				break;
			case ACTIVE:
				this.handleMessage(buf);
				break;
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		log.info("Closed: {}", this.toString());

		final var prevState = this.state;
		this.state = ChannelState.INACTIVE;
		this.inboundMessageSink.onComplete();

		if (prevState == ChannelState.ACTIVE) {
			// only send out event if peer was previously active
			this.peerEventDispatcher.dispatch(PeerDisconnected.create(this));
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		log.warn("Exception on {}: {}", this.toString(), cause.getMessage());
		ctx.close();
	}

	private void write(byte[] data) {
		this.nettyChannel.writeAndFlush(data);
	}

	public Result<Object> send(byte[] data) {
		synchronized (this.lock) {
			if (this.state != ChannelState.ACTIVE) {
				return ERROR_IO.with("Unable to send data", "Channel is inactive").result();
			} else {
				try {
					final var baos = new ByteArrayOutputStream();
					this.frameCodec.writeFrame(data, baos);
					this.write(baos.toByteArray());
					this.outMessagesStats.tick();
					return Result.ok(new Object());
				} catch (IOException e) {
					return ERROR_IO.with("Unable to send data", e.getMessage()).result();
				}
			}
		}
	}

	public long sentMessagesRate() {
		return this.outMessagesStats.currentRate();
	}

	public void disconnect() {
		synchronized (this.lock) {
			this.nettyChannel.close();
		}
	}

	public NodeId getRemoteNodeId() {
		return this.remoteNodeId;
	}

	public boolean isInbound() {
		return !this.isInitiator;
	}

	public boolean isOutbound() {
		return this.isInitiator;
	}

	public Optional<RadixNodeUri> getUri() {
		return this.uri;
	}

	public InetSocketAddress getRemoteSocketAddress() {
		return (InetSocketAddress) this.nettyChannel.remoteAddress();
	}

	@Override
	public String toString() {
		final var hostString = nettyChannel.remoteAddress() instanceof InetSocketAddress
			? ((InetSocketAddress) nettyChannel.remoteAddress()).getHostString()
			: "?";
		final var port = nettyChannel.remoteAddress() instanceof InetSocketAddress
			? ((InetSocketAddress) nettyChannel.remoteAddress()).getPort()
			: 0;
		return String.format(
			"{%s %s@%s:%s | %s}",
			isInitiator ? "<-" : "->",
			remoteNodeId != null ? addressing.forNodes().of(this.remoteNodeId.getPublicKey()) : "?",
			hostString,
			port,
			state
		);
	}
}
