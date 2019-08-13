package org.radix.network2.transport.udp;

import java.io.IOException;

import org.radix.network2.transport.TransportMetadata;

/**
 * Factory interface for creating UDP sockets and channels.
 * Could potentially be split into inbound and outbound factories.
 */
public interface UDPSocketFactory {

	/**
	 * Creates a {@link UDPSocket} for servers/inbound connections.
	 *
	 * @param metadata local metadata for socket creation
	 * @return the created socket
	 * @throws IOException if an error occurs creating the socket
	 */
	UDPSocket createServerSocket(TransportMetadata metadata) throws IOException;

	/**
	 * Creates a {@link UDPChannel} for outbound connections.
	 *
	 * @param metadata remote metadata for channel creation
	 * @return the created channel
	 * @throws IOException if an error occurs creating the channel
	 */
	UDPChannel createClientChannel(TransportMetadata metadata) throws IOException;

}