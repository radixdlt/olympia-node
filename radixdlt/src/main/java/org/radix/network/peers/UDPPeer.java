package org.radix.network.peers;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URI;
import java.util.concurrent.Semaphore;

import org.radix.logging.Logger;
import org.radix.logging.Logging;
import org.radix.network.Protocol;
import org.radix.network.PublicInetAddress;
import org.radix.network.messaging.Message;
import com.radixdlt.serialization.Polymorphic;
import com.radixdlt.serialization.SerializerId2;
import com.google.common.annotations.VisibleForTesting;

@SerializerId2("network.peer")
public class UDPPeer extends Peer implements Polymorphic
{
	private static final Logger networkLog = Logging.getLogger("network");
	private static final Logger messagingLog = Logging.getLogger("messaging");

	private DatagramSocket		socket = null;
	private Semaphore			handshake = new Semaphore(1);
	private PublicInetAddress	localAddress;

	// Used by serializer
	UDPPeer()
	{
		super();
	}

	public UDPPeer(DatagramSocket socket, URI host, Peer peer, PublicInetAddress localAddress) throws IOException
	{
		super(host, peer);

		this.socket = socket;
		this.localAddress = localAddress;

		connect();

		networkLog.debug("Connectioned opened on "+toString());
	}

	@VisibleForTesting
	public UDPPeer(URI host, Void doNotUseThisConstructor) {
		super(host);
	}

	@Override
	public String toString()
	{
		return "UDP "+super.toString();
	}

	@Override
	public void send(Message message) throws IOException
	{
		byte[] messageBytes;
		try {
			messageBytes = message.toByteArray();
		} catch (Exception e) {
			messagingLog.error(String.format("Serialization of message '%s' for '%s failed: %s",
				message.getCommand(), this, e), e);
			throw e;
		}

		// NAT: encode source and dest address to work behind NAT and userland proxies (Docker for Windows/Mac)
		byte[] rawSourceAddress = localAddress.get().getAddress();
		InetAddress destAddress = InetAddress.getByName(getURI().getHost());
		byte[] rawDestAddress = destAddress.getAddress();

		// +1: one byte can hold both address lengths (max IPv6 address length is 8 bytes)
		byte[] datagramBytes = new byte[messageBytes.length + rawSourceAddress.length + rawDestAddress.length + 2];

		if (datagramBytes.length > 65535)
			throw new IOException("Datagram packet of type " + message.getClass().getSimpleName() + " to "+this+" of size "+datagramBytes.length+" is too large");

		// MSB: switch between old/new protocol format
		int flags = 0x80 | (localAddress.get() instanceof Inet6Address ? 0x02 : 0x00) |	(destAddress instanceof Inet6Address ? 0x01 : 0x00);
		datagramBytes[0] = (byte) flags;
		assert rawSourceAddress.length == 4 || rawSourceAddress.length == 16;
		assert rawDestAddress.length == 4 || rawDestAddress.length == 16;
		java.lang.System.arraycopy(rawSourceAddress, 0, datagramBytes, 1, rawSourceAddress.length);
		java.lang.System.arraycopy(rawDestAddress, 0, datagramBytes, 1 + rawSourceAddress.length, rawDestAddress.length);
		java.lang.System.arraycopy(messageBytes, 0, datagramBytes, 1 + rawSourceAddress.length + rawDestAddress.length, messageBytes.length);
//		networkLog.debug("Docker: " + localAddress.get().getHostAddress() + ">" + destAddress.getHostAddress());

		// TODO if peer address is a host name, then a DNS lookup will trigger for the IP, slowing performance
		this.socket.send(new DatagramPacket(datagramBytes, datagramBytes.length, InetAddress.getByName(getURI().getHost()), getURI().getPort()));

		messagingLog.debug(message.toString()+" bytes "+datagramBytes.length);
	}

	@Override
	public boolean isHandshaked()
	{
		return handshake.availablePermits() == 0;
	}

	@Override
	public void handshake()
	{
		if (!handshake.tryAcquire())
			throw new IllegalStateException("Handshake already performed!");

		// Handshake achieved
		if (handshake.availablePermits() == 0)
			onConnected();
	}

	@Override
	public void connect() throws SocketException
	{
		onConnecting();

		if (!isHandshaked())
			handshake();
	}

	@Override
	void onConnected()
	{
		super.onConnected();

		addProtocol(Protocol.UDP);
	}
}
