package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.Transport;
import org.radix.network2.transport.TransportControl;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.radixdlt.universe.Universe;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

final class NettyUDPTransportImpl implements Transport {
	private static final Logger log = Logging.getLogger("transport.udp");

	private final TransportMetadata localMetadata;
	private final UDPTransportControlFactory controlFactory;
	private final UDPTransportOutboundConnectionFactory connectionFactory;

	private final int inboundProcessingThreads;
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
			providedHost = config.getNetworkAddress("0.0.0.0");
		}
		String portString = localMetadata.get(UDPConstants.METADATA_UDP_PORT);
		final int port;
		if (portString == null) {
			port = config.getNetworkPort(Modules.get(Universe.class).getPort());
		} else {
			port = Integer.parseInt(portString);
		}

		this.localMetadata = localMetadata;
		this.controlFactory = controlFactory;
		this.connectionFactory = connectionFactory;
		this.inboundProcessingThreads = config.getInboundProcessingThreads(1);
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
	public void start(InboundMessageConsumer messageSink) {
	    NioEventLoopGroup group = new NioEventLoopGroup(inboundProcessingThreads);

	    Bootstrap b = new Bootstrap();
	    b.group(group)
	        .channel(NioDatagramChannel.class)
	        .handler(new ChannelInitializer<NioDatagramChannel>() {
	            @Override
	            public void initChannel(NioDatagramChannel ch) throws Exception {
	                ch.pipeline()
	                	.addLast("onboard", new NettyMessageHandler(ch, natHandler, messageSink));
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
	    	throw new UncheckedIOException("While closing channel", e);
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
		return String.format("%s[%s|%s]", getClass().getSimpleName(), bindAddress, inboundProcessingThreads);
	}

	private void closeSafely(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (IOException | UncheckedIOException e) {
				log.warn("While closing " + c, e);
			}
		}
	}
}
