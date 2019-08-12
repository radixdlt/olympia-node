package org.radix.network2.transport.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public final class UDPChannelImpl implements UDPChannel {

	private final DatagramChannel channel;

	public UDPChannelImpl(DatagramChannel channel) {
		this.channel = channel;
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}

	@Override
	public void write(ByteBuffer[] byteBuffers) throws IOException {
		this.channel.write(byteBuffers);
	}

}
