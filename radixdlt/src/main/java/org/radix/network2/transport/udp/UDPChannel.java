package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface UDPChannel extends Closeable {

	void write(ByteBuffer[] byteBuffers) throws IOException;

}