package org.radix.network2.transport;

/**
 * Transport interface for various transport implementations.
 */
public interface Transport {

	/**
	 * Returns the name of this transport.
	 *
	 * @return the name of this transport.
	 */
	String getName();

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
