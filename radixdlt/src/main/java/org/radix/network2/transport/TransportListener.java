package org.radix.network2.transport;

import java.io.Closeable;

import org.radix.network2.messaging.InboundMessageConsumer;

/**
 * Listener for a specific transport's inbound messages.
 */
public interface TransportListener extends Closeable {

	/**
	 * Starts the listener with the provided message sink.
	 * The listener is expected to call the message sink with each inbound
	 * message received.
	 *
	 * @param messageSink the message consumer
	 */
	void start(InboundMessageConsumer messageSink);

}
