package org.radix.network2.transport;

import java.io.Closeable;

/**
 * Transport interface for various transport implementations.
 */
public interface Transport extends Closeable {

	/**
	 * Returns the name of this transport.
	 *
	 * @return the name of this transport.
	 */
	String name();

	/**
	 * Returns the control interface for this transport.
	 *
	 * @return the control interface for this transport.
	 */
	TransportControl control();

	/**
	 * Returns the metadata for this transport.
	 * <p>
	 * As an example, the metadata for a TCP or UDP based transport
	 * will include an address and a port.
	 *
	 * @return the metadata for this transport.
	 */
	TransportMetadata metadata();
}
