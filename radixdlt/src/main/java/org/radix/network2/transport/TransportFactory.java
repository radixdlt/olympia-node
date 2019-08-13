package org.radix.network2.transport;

import java.io.IOException;

/**
 * Factory for creating a specific kind of transport given appropriate metadata.
 */
public interface TransportFactory {

	/**
	 * Creates the type of transport this factory produces using the specified
	 * metadata.
	 *
	 * @param metadata The transport's metadata
	 * @return The new transport
	 * @throws IOException if the metadata is invalid, or the transport could not be created
	 */
	Transport create(TransportMetadata metadata) throws IOException;

}
