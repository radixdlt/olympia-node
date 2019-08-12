package org.radix.network2.transport.udp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network2.transport.TransportMetadata;
import org.radix.properties.RuntimeProperties;

import com.radixdlt.universe.Universe;

//FIXME: dependency on Modules.get(Universe.class) for universe port
//FIXME: dependency on Modules.get(RuntimeProperties.class) for UDP configuration
public final class UDPSocketFactoryImpl implements UDPSocketFactory {
	private static final Logger log = Logging.getLogger("transport.udp");

	@Override
	@SuppressWarnings("resource")
	// Resource warning suppression OK here -> caller is responsible
	public UDPSocketImpl createServerSocket(TransportMetadata metadata) throws IOException {
		String providedHost = metadata.get(UDPConstants.METADATA_UDP_HOST);
		if (providedHost == null) {
			if (Modules.get(RuntimeProperties.class).has("network.address")) {
				providedHost = Modules.get(RuntimeProperties.class).get("network.address", "127.0.0.1");
			} else {
				providedHost = "0.0.0.0";
			}
		}
		String portString = metadata.get(UDPConstants.METADATA_UDP_PORT);
		int port = portString == null ? Modules.get(Universe.class).getPort() : Integer.parseInt(portString);
		InetSocketAddress listenSocketAddress = new InetSocketAddress(providedHost, port);

		// FIXME: Should really have a factory for sockets, rather than creating here
		DatagramSocket serverSocket = new DatagramSocket(listenSocketAddress);
    	serverSocket.setReceiveBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
    	serverSocket.setSendBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
    	if (log.hasLevel(Logging.DEBUG)) {
    		log.debug(String.format("UDP server socket %s Receive/Send buffer size: %s/%s",
    			serverSocket.getLocalSocketAddress(), serverSocket.getReceiveBufferSize(), serverSocket.getSendBufferSize()));
    	}
    	return new UDPSocketImpl(serverSocket);
	}

	@Override
	@SuppressWarnings("resource")
	// Resource warning suppression OK here -> caller is responsible
	public UDPChannel createClientChannel(TransportMetadata metadata) throws IOException {
		InetAddress remoteHost = InetAddress.getByName(metadata.get(UDPConstants.METADATA_UDP_HOST));
		int remotePort = Integer.parseInt(metadata.get(UDPConstants.METADATA_UDP_PORT));
		InetSocketAddress remote = new InetSocketAddress(remoteHost, remotePort);
		DatagramChannel channel = DatagramChannel.open();
		channel.setOption(StandardSocketOptions.SO_SNDBUF, Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
		channel.setOption(StandardSocketOptions.SO_RCVBUF, Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
		if (log.hasLevel(Logging.DEBUG)) {
			log.debug(String.format("UDP outbound socket %s rcv/snd buf size %s/%s",
				channel.getLocalAddress(), channel.getOption(StandardSocketOptions.SO_RCVBUF), channel.getOption(StandardSocketOptions.SO_SNDBUF)));
		}
		channel.connect(remote);
		return new UDPChannelImpl(channel);
	}

}
