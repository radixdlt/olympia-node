package org.radix.network2.transport.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Objects;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.modules.Modules;
import org.radix.network.PublicInetAddress;
import org.radix.network2.messaging.InboundMessage;
import org.radix.network2.messaging.InboundMessageConsumer;
import org.radix.network2.transport.StaticTransportMetadata;
import org.radix.network2.transport.TransportInfo;
import org.radix.network2.transport.TransportListener;
import org.radix.network2.transport.TransportMetadata;
import org.radix.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;

// FIXME: dependency on PublicInetAddress singleton for NAT handling
// FIXME: dependency on Modules.get(Universe.class) for universe port
public class UDPTransportListener implements TransportListener {
	private static final Logger log = Logging.getLogger("transport.udp");

	private final DatagramSocket serverSocket;
	private final Thread listeningThread;

	private InboundMessageConsumer messageSink;

	public UDPTransportListener(TransportMetadata metadata) throws SocketException {
		String providedHost = metadata.get(UDPConstants.METADATA_UDP_HOST);
		if (providedHost == null) {
			providedHost = "0.0.0.0"; // I'm all ears
		}
		String portString = metadata.get(UDPConstants.METADATA_UDP_PORT);
		int port = portString == null ? Modules.get(Universe.class).getPort() : Integer.parseInt(portString);
		InetSocketAddress listenSocketAddress = new InetSocketAddress(providedHost, port);

		// FIXME: Should really have a factory for sockets, rather than creating here
		this.serverSocket = new DatagramSocket(listenSocketAddress);
    	this.serverSocket.setReceiveBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
    	this.serverSocket.setSendBufferSize(Modules.get(RuntimeProperties.class).get("network.udp.buffer", 1 << 18));
    	if (log.hasLevel(Logging.DEBUG)) {
    		log.debug(String.format("UDP server socket %s Receive/Send buffer size: %s/%s",
    			this.serverSocket.getLocalSocketAddress(), this.serverSocket.getReceiveBufferSize(), this.serverSocket.getSendBufferSize()));
    	}

		// Start listening thread
		this.listeningThread = new Thread(this::inboundMessageReceiver, getClass().getSimpleName() + " UDP listening");
		this.listeningThread.setDaemon(true);
	}

	@Override
	public void start(InboundMessageConsumer messageSink) {
		this.messageSink = Objects.requireNonNull(messageSink);
		this.listeningThread.start();
	}

	@Override
	public void close() throws IOException {
		this.listeningThread.interrupt();
		try {
			this.listeningThread.join();
		} catch (InterruptedException e) {
			// Not much we can do here.  Hope the upstream stuff works out OK.
			log.error("Thread did not exit before interrupt");
			Thread.currentThread().interrupt();
		}
	}

	private void inboundMessageReceiver() {
		final byte[] buf = new byte[65536];
	    final DatagramPacket dp = new DatagramPacket(buf, buf.length);

	    while (!serverSocket.isClosed()) {
	    	try {
	    		// Reset pointers
	    		dp.setData(buf);
	    		serverSocket.receive(dp);
	    	} catch (IOException e) {
	    		// Ignore and continue
	    		continue;
	    	}

	    	try {
	    		final byte[] data = dp.getData();
	    		final int offset = dp.getOffset();
	    		final int length = dp.getLength();

	    		// part of the NAT address validation process
				if (!PublicInetAddress.getInstance().endValidation(data, offset, length)) {
					// Clone data and put in queue
					InetAddress peerAddress = dp.getAddress();
					int extraOffset = 0;
					if (length > 0 && (data[offset] & 0x80) != 0) {
						// NAT: decode the source and dest addresses (see UDPPeer for how this is encoded)
						byte[] rawLocalAddress = new byte[(data[offset] & 0x01) != 0 ? 16 : 4];
						byte[] rawPeerAddress = new byte[(data[offset] & 0x02) != 0 ? 16 : 4];

						if ((rawPeerAddress.length + rawLocalAddress.length) < length) {
							System.arraycopy(data, offset + 1, rawPeerAddress, 0, rawPeerAddress.length);
							InetAddress addr = InetAddress.getByAddress(rawPeerAddress);
							// TODO: if addr is previously unknown we need to challenge it to prevent peer table poisoning:
							// See "Proposed solution for Routing Table Poisoning" in https://pdfs.semanticscholar.org/3990/e316c8ecedf8398bd6dc167d92f094525920.pdf
							if (!PublicInetAddress.isPublicUnicastInetAddress(peerAddress) && PublicInetAddress.isPublicUnicastInetAddress(addr)) {
								peerAddress = addr;
							}

							System.arraycopy(data, offset + 1 + rawPeerAddress.length, rawLocalAddress, 0, rawLocalAddress.length);
							InetAddress localAddr = InetAddress.getByAddress(rawLocalAddress);
							if (PublicInetAddress.isPublicUnicastInetAddress(localAddr)) {
								PublicInetAddress.getInstance().startValidation(localAddr);
							}
							extraOffset = 1 + rawPeerAddress.length + rawLocalAddress.length;
						}
					}

					// NAT validated, just make the message available
					byte[] newData = new byte[length - extraOffset];
					System.arraycopy(data, offset + extraOffset, newData, 0, length - extraOffset);
					TransportInfo source = TransportInfo.of(
						UDPConstants.UDP_NAME,
						StaticTransportMetadata.of(
							UDPConstants.METADATA_UDP_HOST, peerAddress.getHostAddress(),
							UDPConstants.METADATA_UDP_PORT, String.valueOf(dp.getPort())
						)
					);
					messageSink.accept(InboundMessage.of(source, newData));
				}
	    	} catch (IOException e) {
	    		log.error("While processing inbound message", e);
	    	}
	    }
	}
}
