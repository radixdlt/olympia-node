package org.radix.network2.transport;

import java.io.Closeable;

import org.radix.network2.messaging.InboundMessageConsumer;

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
	 * Returns the local node's metadata for this transport.
	 * <p>
	 * As an example, the metadata for a TCP or UDP based transport
	 * will include an address and a port.
	 *
	 * @return the local metadata for this transport.
	 */
	TransportMetadata localMetadata();

	/**
	 * Starts the transport's listener with the provided message sink.
	 * The listener is expected to call the message sink with each inbound
	 * message received.
	 *
	 * @param messageSink the message consumer
	 */
	void start(InboundMessageConsumer messageSink);
}
