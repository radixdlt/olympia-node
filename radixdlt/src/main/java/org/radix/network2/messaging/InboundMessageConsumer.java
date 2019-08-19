package org.radix.network2.messaging;

import java.util.function.Consumer;

/**
 * Specific type for consuming {@code InboundMessage} objects.
 *
 * @see org.radix.network2.transport.Transport#start(InboundMessageConsumer)
 */
@FunctionalInterface
public interface InboundMessageConsumer extends Consumer<InboundMessage> {

	// Nothing added here

}
