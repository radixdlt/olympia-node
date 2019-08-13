package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for testability.
 * Primarily used to allow straightforward mocking/replacement of DatagramChannel.
 */
interface UDPChannel extends Closeable {

	/**
	 * Gathering write.
	 *
	 * @param byteBuffers the buffers to write, in order
	 * @throws IOException if an IO exception occurs
	 */
	void write(ByteBuffer[] byteBuffers) throws IOException;

}