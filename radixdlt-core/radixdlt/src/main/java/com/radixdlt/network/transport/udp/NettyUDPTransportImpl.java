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

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.messaging.InboundMessage;
import hu.akarnokd.rxjava3.operators.FlowableTransformers;
import io.reactivex.rxjava3.core.BackpressureOverflowStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.network.transport.StaticTransportMetadata;
import com.radixdlt.network.transport.Transport;
import com.radixdlt.network.transport.TransportControl;
import com.radixdlt.network.transport.TransportMetadata;
import com.radixdlt.network.transport.TransportOutboundConnection;
import com.radixdlt.network.transport.netty.LogSink;
import com.radixdlt.network.transport.netty.LoggingHandler;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

final class NettyUDPTransportImpl implements Transport {
	private static final Logger log = LogManager.getLogger();

	private static final int CHANNELS_BUFFER_SIZE = 1;

	// Set this to true to see a dump of packet data
	protected static final boolean DEBUG_DATA = false;

	// Default values if none specified in either localMetadata or config
	@VisibleForTesting
	static final String DEFAULT_HOST = "0.0.0.0";
	@VisibleForTesting
	static final int    DEFAULT_PORT = 30000;

	static final int MAX_DATAGRAM_SIZE = 65536;
	static final int RCV_BUF_SIZE = MAX_DATAGRAM_SIZE * 4;
	static final int SND_BUF_SIZE = MAX_DATAGRAM_SIZE * 4;

	private final TransportMetadata localMetadata;
	private final UDPTransportControlFactory controlFactory;
	private final UDPTransportOutboundConnectionFactory connectionFactory;

	private final SystemCounters counters;
	private final int priority;
	private final int messageBufferSize;
	private final AtomicInteger threadCounter = new AtomicInteger(0);
	private final InetSocketAddress bindAddress;
	private final NatHandler natHandler;
	private final Object channelLock = new Object();

	private DatagramChannel channel;
	private TransportOutboundConnection outbound;
	private TransportControl control;

	private final PublishProcessor<Flowable<InboundMessage>> channels = PublishProcessor.create();

	@Inject
	NettyUDPTransportImpl(
		SystemCounters counters,
		UDPConfiguration config,
		@Named("local") TransportMetadata localMetadata,
		UDPTransportControlFactory controlFactory,
		UDPTransportOutboundConnectionFactory connectionFactory,
		NatHandler natHandler
	) {
		this.counters = Objects.requireNonNull(counters);

		String providedHost = localMetadata.get(UDPConstants.METADATA_HOST);
		if (providedHost == null) {
			providedHost = config.networkAddress(DEFAULT_HOST);
		}
		String portString = localMetadata.get(UDPConstants.METADATA_PORT);
		final int port;
		if (portString == null) {
			port = config.networkPort(DEFAULT_PORT);
		} else {
			port = Integer.parseInt(portString);
		}
		this.localMetadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_HOST, providedHost,
			UDPConstants.METADATA_PORT, String.valueOf(port)
		);
		this.controlFactory = controlFactory;
		this.connectionFactory = connectionFactory;
		this.priority = config.priority(1000);
		this.messageBufferSize = config.messageBufferSize(2);
		this.bindAddress = new InetSocketAddress(providedHost, port);
		this.natHandler = natHandler;
	}

	@Override
	public String name() {
		return UDPConstants.NAME;
	}

	@Override
	public TransportControl control() {
		return control;
	}

	@Override
	public TransportMetadata localMetadata() {
		return localMetadata;
	}

	@Override
	public boolean canHandle(byte[] message) {
		return (message == null) || (message.length <= UDPConstants.MAX_PACKET_LENGTH);
	}

	@Override
	public int priority() {
		return this.priority;
	}

	@Override
	public Flowable<InboundMessage> start() {
		if (log.isInfoEnabled()) {
			log.info("UDP transport {}", localAddress());
		}
		MultithreadEventLoopGroup group = new NioEventLoopGroup(1, this::createThread);

		Bootstrap b = new Bootstrap();
		b.group(group)
			.channel(NioDatagramChannel.class)
			.handler(new ChannelInitializer<NioDatagramChannel>() {
				@Override
				public void initChannel(NioDatagramChannel ch) {
					final var messageHandler = new UDPNettyMessageHandler(counters, messageBufferSize, natHandler);
					channels.onNext(messageHandler.inboundMessageRx());

					ch.config()
						.setReceiveBufferSize(RCV_BUF_SIZE)
						.setSendBufferSize(SND_BUF_SIZE)
						.setOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(MAX_DATAGRAM_SIZE));
					if (log.isDebugEnabled()) {
						ch.pipeline()
							.addLast(new LoggingHandler(LogSink.using(log), DEBUG_DATA));
					}
					ch.pipeline()
						.addLast("onboard", messageHandler);
					ch.closeFuture()
						.addListener(f -> messageHandler.shutdownRx());
				}
	        });
	    try {
	    	synchronized (channelLock) {
	    		close();
	    		this.channel = (DatagramChannel) b.bind(bindAddress).sync().channel();
	    		this.control = controlFactory.create(this.channel, connectionFactory, natHandler);
	    	}
	    } catch (InterruptedException e) {
	    	// Abort!
	    	Thread.currentThread().interrupt();
	    } catch (IOException e) {
	    	throw new UncheckedIOException("Error while opening channel", e);
		}

		return channels
			.onBackpressureBuffer(
				CHANNELS_BUFFER_SIZE,
				() -> log.error("UDP channels buffer overflow!"),
				BackpressureOverflowStrategy.DROP_LATEST
			)
			.compose(FlowableTransformers.flatMapAsync(v -> v, Schedulers.single(), false));
	}

	@Override
	public void close() throws IOException {
    	synchronized (this.channelLock) {
    		closeSafely(control);
    		closeSafely(outbound);
    		if (channel != null) {
    			closeSafely(channel::close);
    		}
    		this.control = null;
    		this.outbound = null;
    		this.channel = null;
    	}
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), localAddress());
	}

	private String localAddress() {
		return String.format("%s:%s", bindAddress.getAddress().getHostAddress(), bindAddress.getPort());
	}

	private Thread createThread(Runnable r) {
		String threadName = String.format("UDP handler %s - %s", localAddress(), threadCounter.incrementAndGet());
		if (log.isDebugEnabled()) {
			log.debug("New thread: {}", threadName);
		}
		return new Thread(r, threadName);
	}

	private void closeSafely(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException | UncheckedIOException e) {
				log.warn("Error while closing " + c, e);
			}
		}
	}
}
