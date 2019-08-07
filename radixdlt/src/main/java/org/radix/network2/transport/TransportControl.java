package org.radix.network2.transport;

import java.util.concurrent.CompletableFuture;

import org.radix.network2.addressbook.Peer;

public interface TransportControl {

	/**
	 * Open an outbound connection to a peer.
	 * The corresponding inbound connection will be added to the pool of listening connections
	 * automatically.
	 *
	 * @param peer The peer to connect to
	 * @return A {@link CompletableFuture} returning an outbound transport connection once the connection is open
	 */
	CompletableFuture<TransportOutboundConnection> open(Peer peer);

}
