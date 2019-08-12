package org.radix.network2.transport;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

public interface TransportControl extends Closeable {

	/**
	 * Open an outbound connection to a peer.
	 * The corresponding inbound connection will be added to the pool of listening connections
	 * automatically.
	 *
	 * @return A {@link CompletableFuture} returning an outbound transport connection once the connection is open
	 */
	CompletableFuture<TransportOutboundConnection> open();

}
