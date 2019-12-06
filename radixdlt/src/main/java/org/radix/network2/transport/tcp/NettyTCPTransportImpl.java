package org.radix.network2.transport.tcp;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

final class NettyTCPTransportImpl implements NettyTCPTransport {
	private static final Logger log = Logging.getLogger("transport.tcp");

	// Default values if none specified in either localMetadata or config
	private static final String DEFAULT_HOST = "0.0.0.0";
	private static final int    DEFAULT_PORT = 30000;

	private static final int RCV_BUF_SIZE = TCPConstants.MAX_PACKET_LENGTH * 2;
	private static final int SND_BUF_SIZE = TCPConstants.MAX_PACKET_LENGTH * 2;
	private static final int BACKLOG_SIZE = 100;

	private final TransportMetadata localMetadata;

	private final int inboundProcessingThreads;
	private final AtomicInteger threadCounter = new AtomicInteger(0);
	private final InetSocketAddress bindAddress;
	private final Object channelLock = new Object();

	private final TCPTransportControl control;

	private Channel channel;
	private Bootstrap outboundBootstrap;



	@Inject
	NettyTCPTransportImpl(
		TCPConfiguration config,
		@Named("local") TransportMetadata localMetadata,
		TCPTransportOutboundConnectionFactory outboundFactory,
		TCPTransportControlFactory controlFactory
	) {
		String providedHost = localMetadata.get(TCPConstants.METADATA_TCP_HOST);
		if (providedHost == null) {
			providedHost = config.networkAddress(DEFAULT_HOST);
		}
		String portString = localMetadata.get(TCPConstants.METADATA_TCP_PORT);
		final int port;
		if (portString == null) {
			port = config.networkPort(DEFAULT_PORT);
		} else {
			port = Integer.parseInt(portString);
		}

		this.localMetadata = StaticTransportMetadata.of(
			TCPConstants.METADATA_TCP_HOST, providedHost,
			TCPConstants.METADATA_TCP_PORT, String.valueOf(port)
		);
		this.control = controlFactory.create(outboundFactory, this);
		this.inboundProcessingThreads = config.processingThreads(1);
		this.bindAddress = new InetSocketAddress(providedHost, port);
	}

	@Override
	public String name() {
		return TCPConstants.TCP_NAME;
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
	public void start(InboundMessageConsumer messageSink) {
		log.info(String.format("TCP transport %s, threads: %s", localAddress(), this.inboundProcessingThreads));

		EventLoopGroup serverGroup = new NioEventLoopGroup(1);
		MultithreadEventLoopGroup workerGroup = new NioEventLoopGroup(this.inboundProcessingThreads, this::createThread);

		this.outboundBootstrap = new Bootstrap();
		this.outboundBootstrap.group(workerGroup)
			.channel(NioSocketChannel.class)
			.option(ChannelOption.SO_KEEPALIVE, true)
			.handler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					setupChannel(ch, messageSink);
				}
			});

		ServerBootstrap b = new ServerBootstrap();
		b.group(serverGroup, workerGroup)
			.channel(NioServerSocketChannel.class)
			.option(ChannelOption.SO_BACKLOG, BACKLOG_SIZE)
			.childOption(ChannelOption.SO_KEEPALIVE, true)
			.handler(new LoggingHandler(LogLevel.INFO))
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					setupChannel(ch, messageSink);
				}
			});
		try {
			synchronized (channelLock) {
				close();
				this.channel = b.bind(this.bindAddress).sync().channel();
			}
		} catch (InterruptedException e) {
			// Abort!
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			throw new UncheckedIOException("Error while opening channel", e);
		}
	}

	private void setupChannel(SocketChannel ch, InboundMessageConsumer messageSink) {
		final int packetLength = TCPConstants.MAX_PACKET_LENGTH + TCPConstants.LENGTH_HEADER;
		final int headerLength = TCPConstants.LENGTH_HEADER;
		ch.config()
			.setReceiveBufferSize(RCV_BUF_SIZE)
			.setSendBufferSize(SND_BUF_SIZE)
			.setOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(packetLength));
		ch.pipeline()
			.addLast("connections", control.handler())
			.addLast("unpack", new LengthFieldBasedFrameDecoder(packetLength, 0, headerLength, 0, headerLength))
			.addLast("onboard", new TCPNettyMessageHandler(messageSink));
		ch.pipeline()
			.addLast("pack", new LengthFieldPrepender(headerLength));
	}

	@Override
	public ChannelFuture createChannel(String host, int port) {
		return this.outboundBootstrap.connect(host, port);
	}

	@Override
	public void close() throws IOException {
		synchronized (this.channelLock) {
			closeSafely(this.control);
			if (this.channel != null) {
				closeSafely(this.channel::close);
			}
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
		String threadName = String.format("TCP handler %s - %s", localAddress(), threadCounter.incrementAndGet());
		if (log.hasLevel(Logging.DEBUG)) {
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
