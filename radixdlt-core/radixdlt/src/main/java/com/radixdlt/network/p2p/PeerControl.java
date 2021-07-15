package com.radixdlt.network.p2p;

import java.time.Duration;

public interface PeerControl {

	/**
	 * Bans a peer for a specified period of time.
	 * Any open connections to a banned peer are immediately closed.
	 */
	void banPeer(NodeId nodeId, Duration banDuration, String reason);

	default void banPeerForever(NodeId nodeId, String reason) {
		banPeer(nodeId, Duration.ofMillis(Long.MAX_VALUE), reason);
	}
}
