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

package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;
import org.radix.network2.transport.netty.LogSink;
import org.radix.network2.transport.netty.LoggingHandler;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

final class NettyUDPTransportImpl implements Transport {
	private static final Logger log = LogManager.getLogger("transport.udp");

	// Set this to true to see a dump of packet data
	protected static final boolean DEBUG_DATA = false;

	// Default values if none specified in either localMetadata or config
	private static final String DEFAULT_HOST = "0.0.0.0";
	private static final int    DEFAULT_PORT = 30000;

	static final int MAX_DATAGRAM_SIZE = 65536;
	static final int RCV_BUF_SIZE = MAX_DATAGRAM_SIZE * 4;
	static final int SND_BUF_SIZE = MAX_DATAGRAM_SIZE * 4;

	private final TransportMetadata localMetadata;
	private final UDPTransportControlFactory controlFactory;
	private final UDPTransportOutboundConnectionFactory connectionFactory;

	private final int priority;
	private final int inboundProcessingThreads;
	private final AtomicInteger threadCounter = new AtomicInteger(0);
	private final InetSocketAddress bindAddress;
	private final PublicInetAddress natHandler;
	private final Object channelLock = new Object();

	private DatagramChannel channel;
	private TransportOutboundConnection outbound;
	private TransportControl control;


	@Inject
	NettyUDPTransportImpl(
		UDPConfiguration config,
		@Named("local") TransportMetadata localMetadata,
		UDPTransportControlFactory controlFactory,
		UDPTransportOutboundConnectionFactory connectionFactory,
		PublicInetAddress natHandler
	) {
		String providedHost = localMetadata.get(UDPConstants.METADATA_UDP_HOST);
		if (providedHost == null) {
			providedHost = config.networkAddress(DEFAULT_HOST);
		}
		String portString = localMetadata.get(UDPConstants.METADATA_UDP_PORT);
		final int port;
		if (portString == null) {
			port = config.networkPort(DEFAULT_PORT);
		} else {
			port = Integer.parseInt(portString);
		}
		this.localMetadata = StaticTransportMetadata.of(
			UDPConstants.METADATA_UDP_HOST, providedHost,
			UDPConstants.METADATA_UDP_PORT, String.valueOf(port)
		);
		this.controlFactory = controlFactory;
		this.connectionFactory = connectionFactory;
		this.inboundProcessingThreads = config.processingThreads(1);
		if (this.inboundProcessingThreads < 0) {
			throw new IllegalStateException("Illegal number of UDP inbound threads: " + this.inboundProcessingThreads);
		}
		this.priority = config.priority(1000);
		this.bindAddress = new InetSocketAddress(providedHost, port);
		this.natHandler = natHandler;
	}

	@Override
	public String name() {
		return UDPConstants.UDP_NAME;
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
	public void start(InboundMessageConsumer messageSink) {
		log.info(String.format("UDP transport %s, threads: %s", localAddress(), inboundProcessingThreads));
		MultithreadEventLoopGroup group = new NioEventLoopGroup(inboundProcessingThreads, this::createThread);

	    Bootstrap b = new Bootstrap();
	    b.group(group)
	        .channel(NioDatagramChannel.class)
	        .handler(new ChannelInitializer<NioDatagramChannel>() {
	            @Override
	            public void initChannel(NioDatagramChannel ch) throws Exception {
	            	ch.config()
	            		.setReceiveBufferSize(RCV_BUF_SIZE)
	            		.setSendBufferSize(SND_BUF_SIZE)
	            		.setOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(MAX_DATAGRAM_SIZE));
	        		if (log.isDebugEnabled()) {
	        			LogSink ls = LogSink.forDebug(log);
	        			ch.pipeline()
	        				.addLast(new LoggingHandler(ls, DEBUG_DATA));
	        		}
	                ch.pipeline()
	                	.addLast("onboard", new UDPNettyMessageHandler(natHandler, messageSink));
	            }
	        });
	    try {
	    	synchronized (channelLock) {
	    		close();
	    		this.channel = (DatagramChannel) b.bind(bindAddress).sync().channel();
	    		this.control = controlFactory.create(this.channel, connectionFactory);
	    	}
	    } catch (InterruptedException e) {
	    	// Abort!
	    	Thread.currentThread().interrupt();
	    } catch (IOException e) {
	    	throw new UncheckedIOException("Error while opening channel", e);
		}
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
		return String.format("%s[%s|%s]", getClass().getSimpleName(), localAddress(), inboundProcessingThreads);
	}

	private String localAddress() {
		return String.format("%s:%s", bindAddress.getAddress().getHostAddress(), bindAddress.getPort());
	}

	private Thread createThread(Runnable r) {
		String threadName = String.format("UDP handler %s - %s", localAddress(), threadCounter.incrementAndGet());
		if (log.isDebugEnabled()) {
			log.debug("New thread: " + threadName);
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
