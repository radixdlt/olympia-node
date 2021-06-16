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

import com.google.common.util.concurrent.RateLimiter;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyOps;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.network.messaging.InboundMessage;
import com.radixdlt.network.p2p.NodeId;
import com.radixdlt.network.p2p.PeerControl;
import com.radixdlt.network.p2p.RadixNodeUri;
import com.radixdlt.network.p2p.PeerEvent;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult.AuthHandshakeError;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshakeResult.AuthHandshakeSuccess;
import com.radixdlt.network.p2p.transport.handshake.AuthHandshaker;
import com.radixdlt.network.p2p.PeerEvent.PeerConnected;
import com.radixdlt.network.p2p.PeerEvent.PeerDisconnected;
import com.radixdlt.network.p2p.P2PConfig;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.utils.RateCalculator;
import com.radixdlt.utils.functional.Result;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.InvalidCipherTextException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static com.radixdlt.network.messaging.MessagingErrors.IO_ERROR;

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
	private final RateLimiter droppedMessagesRateLimiter = RateLimiter.create(1.0);
	private final PublishProcessor<InboundMessage> inboundMessageSink = PublishProcessor.create();
	private final Flowable<InboundMessage> inboundMessages;

	private final SystemCounters counters;
	private final EventDispatcher<PeerEvent> peerEventDispatcher;
	private final PeerControl peerControl;
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
		SystemCounters counters,
		Serialization serialization,
		SecureRandom secureRandom,
		ECKeyOps ecKeyOps,
		EventDispatcher<PeerEvent> peerEventDispatcher,
		PeerControl peerControl,
		Optional<RadixNodeUri> uri,
		SocketChannel nettyChannel
	) {
		this.counters = Objects.requireNonNull(counters);
		this.peerEventDispatcher = Objects.requireNonNull(peerEventDispatcher);
		this.peerControl = Objects.requireNonNull(peerControl);
		this.uri = Objects.requireNonNull(uri);
		uri.ifPresent(u -> this.remoteNodeId = u.getNodeId());
		this.authHandshaker = new AuthHandshaker(serialization, secureRandom, ecKeyOps, config.networkId());
		this.nettyChannel = Objects.requireNonNull(nettyChannel);

		this.isInitiator = uri.isPresent();

		this.inboundMessages = inboundMessageSink
			.onBackpressureBuffer(
				config.channelBufferSize(),
				() -> {
					this.counters.increment(SystemCounters.CounterType.NETWORKING_TCP_DROPPED_MESSAGES);
					final var logLevel = droppedMessagesRateLimiter.tryAcquire() ? Level.WARN : Level.TRACE;
					log.log(logLevel, "TCP msg buffer overflow, dropping msg");
				},
				BackpressureOverflowStrategy.DROP_LATEST);
	}

	private void initHandshake(NodeId remoteNodeId) throws PublicKeyException {
		final var initiatePacket = authHandshaker.initiate(remoteNodeId.getPublicKey());
		log.trace("Sending auth initiate message to {} [{}]", remoteNodeId, this.nettyChannel.remoteAddress());
		this.write(initiatePacket);
	}

	public Flowable<InboundMessage> inboundMessages() {
		return inboundMessages;
	}

	private void handleHandshakeData(byte[] data) throws IOException, InvalidCipherTextException, PublicKeyException {
		if (this.isInitiator) {
			log.trace("Handling auth response message from {} [{}]", remoteNodeId, this.nettyChannel.remoteAddress());
			final var handshakeResult = this.authHandshaker.handleResponseMessage(data);
			this.finalizeHandshake(handshakeResult);
		} else {
			log.trace("Handling auth initiate message from {} [{}]", remoteNodeId, this.nettyChannel.remoteAddress());
			final var result = this.authHandshaker.handleInitialMessage(data);
			if (result.getFirst() != null) {
				this.write(result.getFirst());
			}
			this.finalizeHandshake(result.getSecond());
		}
	}

	private void finalizeHandshake(AuthHandshakeResult handshakeResult) {
		if (handshakeResult instanceof AuthHandshakeSuccess) {
			final var successResult = (AuthHandshakeSuccess) handshakeResult;
			log.trace("Finalizing successful auth handshake with {} [{}]",
				remoteNodeId, this.nettyChannel.remoteAddress());
			this.remoteNodeId = successResult.getRemoteNodeId();
			this.frameCodec = new FrameCodec(successResult.getSecrets());
			this.state = ChannelState.ACTIVE;
			peerEventDispatcher.dispatch(PeerConnected.create(this));
		} else {
			final var errorResult = (AuthHandshakeError) handshakeResult;
			log.trace("Auth handshake failed with {} [{}] because of: {}. Disconnecting and banning peer.",
				remoteNodeId, this.nettyChannel.remoteAddress(), errorResult.getMsg());
			errorResult.getMaybeNodeId().ifPresent(remoteNodeId ->
				this.peerControl.banPeer(remoteNodeId, Duration.ofHours(1)));
			this.disconnect();
		}
	}

	private void handleMessage(byte[] buf) throws IOException {
		synchronized (this.lock) {
			final var maybeFrame = this.frameCodec.tryReadSingleFrame(buf);
			maybeFrame.ifPresentOrElse(
				frame -> inboundMessageSink.onNext(InboundMessage.of(remoteNodeId, frame)),
				() -> log.error("Failed to read a complete frame from {}", nettyChannel.remoteAddress())
			);
		}
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws PublicKeyException {
		this.state = ChannelState.AUTH_HANDSHAKE;

		log.trace("Peer channel active to {}, isInitiator ?= {}", this.nettyChannel.remoteAddress(), isInitiator);

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
		log.info("Channel {} closed with exception: {}", ctx.channel().remoteAddress(), cause.getMessage());
		ctx.close();
	}

	private void write(byte[] data) {
		this.nettyChannel.writeAndFlush(data);
	}

	public Result<Object> send(byte[] data) {
		synchronized (this.lock) {
			if (this.state != ChannelState.ACTIVE) {
				return IO_ERROR.result();
			} else {
				try {
					final var baos = new ByteArrayOutputStream();
					this.frameCodec.writeFrame(data, baos);
					this.write(baos.toByteArray());
					this.outMessagesStats.tick();
					return Result.ok(new Object());
				} catch (IOException e) {
					return IO_ERROR.result();
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
}
