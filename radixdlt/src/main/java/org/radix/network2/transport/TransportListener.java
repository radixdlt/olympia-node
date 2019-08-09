package org.radix.network2.transport;

import java.io.Closeable;

import org.radix.network2.messaging.InboundMessageConsumer;

public interface TransportListener extends Closeable {

	void start(InboundMessageConsumer messageSink);

}
