package org.radix.network2.transport.udp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;

public interface UDPSocket extends Closeable {

	boolean isClosed();

	void receive(DatagramPacket dp) throws IOException;

}