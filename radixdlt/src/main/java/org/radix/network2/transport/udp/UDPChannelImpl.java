package org.radix.network2.transport.udp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Implementation of {@link UDPChannel} that wraps a {@link DatagramChannel}.
 */
final class UDPChannelImpl implements UDPChannel {

	private final DatagramChannel channel;

	UDPChannelImpl(DatagramChannel channel) {
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
