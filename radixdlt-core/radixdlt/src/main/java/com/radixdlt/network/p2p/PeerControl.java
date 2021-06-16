package com.radixdlt.network.p2p;

import java.time.Duration;

public interface PeerControl {

	/**
	 * Bans a peer for a specified period of time.
	 * Any open connections to a banned peer are immediately closed.
	 */
	void banPeer(NodeId nodeId, Duration banDuration);

	default void banPeerForever(NodeId nodeId) {
		banPeer(nodeId, Duration.ofMillis(Long.MAX_VALUE));
	}
}
