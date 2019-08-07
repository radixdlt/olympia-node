package org.radix.network2.transport;

public interface TransportRegistry {

	/**
	 * Retrieve a definition of a transport given it's common name.
	 *
	 * @param transportName The well-known name of the specified transport, eg "TCP"
	 * @return The transport, or {@code null}
	 */
	Transport get(String transportName);

	/**
	 * Register a transport.
	 *
	 * @param transport The transport to register
	 * @throws TransportException if an error occurs
	 */
	void register(Transport transport) throws TransportException;

}
