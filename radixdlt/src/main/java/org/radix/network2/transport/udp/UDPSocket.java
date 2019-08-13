package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;

/**
 * Testability interface for wrapping a DatagramSocket.
 */
interface UDPSocket extends Closeable {

	/**
	 * Returns {@code true} if socket is closed, false otherwise.
	 *
	 * @return {@code true} if socket is closed, false otherwise
	 */
	boolean isClosed();

	/**
	 * Receive data from underlying socket.
	 * Either provided {@link DatagramPacket} is updated with received data,
	 * or an {@link IOException} is thrown.
	 *
	 * @param dp the {@link DatagramPacket} to update with received data
	 * @throws IOException if an error occurs, including socket closure
	 */
	void receive(DatagramPacket dp) throws IOException;

}