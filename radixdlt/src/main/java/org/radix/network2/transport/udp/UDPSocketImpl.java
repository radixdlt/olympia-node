package org.radix.network2.transport.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class UDPSocketImpl implements UDPSocket {

	private final DatagramSocket datagramSocket;

	public UDPSocketImpl(DatagramSocket serverSocket) {
		this.datagramSocket = serverSocket;
	}

	@Override
	public void close() throws IOException {
		datagramSocket.close();
	}

	@Override
	public boolean isClosed() {
		return datagramSocket.isClosed();
	}

	@Override
	public void receive(DatagramPacket dp) throws IOException {
		datagramSocket.receive(dp);
	}

	@Override
	public String toString() {
		return datagramSocket.toString();
	}

}
