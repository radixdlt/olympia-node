package org.radix.network2.messaging;

import java.util.function.Consumer;

@FunctionalInterface
public interface InboundMessageConsumer extends Consumer<InboundMessage> {

	// Nothing added here

}
