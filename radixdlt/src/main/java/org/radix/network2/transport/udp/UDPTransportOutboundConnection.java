package org.radix.network2.transport.udp;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CompletableFuture;
import org.radix.network.PublicInetAddress;
import org.radix.network2.addressbook.Peer;
import org.radix.network2.transport.SendResult;
import org.radix.network2.transport.TransportException;
import org.radix.network2.transport.TransportMetadata;
import org.radix.network2.transport.TransportOutboundConnection;

class UDPTransportOutboundConnection implements TransportOutboundConnection {

	private final PublicInetAddress localAddress = PublicInetAddress.getInstance();
	private final DatagramChannel channel;
	private final InetAddress remoteHost;
	private final int remotePort;

	public UDPTransportOutboundConnection(Peer peer) {
		try {
			TransportMetadata metadata = peer.connectionData(UDPConstants.UDP_NAME);
			this.remoteHost = InetAddress.getByName(metadata.get(UDPConstants.METADATA_UDP_HOST));
			this.remotePort = Integer.parseInt(metadata.get(UDPConstants.METADATA_UDP_PORT));
			InetSocketAddress remote = new InetSocketAddress(this.remoteHost, this.remotePort);
			this.channel = DatagramChannel.open().connect(remote);
		} catch (IOException e) {
			throw new TransportException("While connecting", e);
		}
	}

	@Override
	public void close() throws IOException {
		this.channel.close();
	}

	@Override
	public CompletableFuture<SendResult> send(byte[] data) {
		return CompletableFuture.supplyAsync(() -> {
			// NAT: encode source and dest address to work behind NAT and userland proxies (Docker for Windows/Mac)
			ByteBuffer rawSourceAddress = ByteBuffer.wrap(localAddress.get().getAddress());
			ByteBuffer rawDestAddress = ByteBuffer.wrap(remoteHost.getAddress());

			int totalSize = data.length + rawSourceAddress.limit() + rawDestAddress.limit() + 1;
			if (totalSize > UDPConstants.MAX_PACKET_LENGTH) {
				return SendResult.failure(new IOException("Datagram packet to " + remoteHost + " of size " + totalSize + " is too large"));
			}

			// MSB: switch between old/new protocol format
			byte[] flags = { getAddressFormat(localAddress.get(), remoteHost) };
			assert rawSourceAddress.limit() == 4 || rawSourceAddress.limit() == 16;
			assert rawDestAddress.limit() == 4 || rawDestAddress.limit() == 16;

			try {
				this.channel.write(new ByteBuffer[] { ByteBuffer.wrap(flags), rawSourceAddress, rawDestAddress, ByteBuffer.wrap(data) });
				return SendResult.complete();
			} catch (IOException exception) {
				return SendResult.failure(exception);
			}
		});
	}

	private byte getAddressFormat(InetAddress src, InetAddress dst) {
		return (byte) (0x80 | (src instanceof Inet6Address ? 0x02 : 0x00) | (dst instanceof Inet6Address ? 0x01 : 0x00));
	}
}
