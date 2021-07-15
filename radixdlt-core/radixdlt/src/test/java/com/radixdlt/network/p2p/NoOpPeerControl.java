package com.radixdlt.network.p2p;

import java.time.Duration;

public final class NoOpPeerControl implements PeerControl {
	@Override
	public void banPeer(NodeId nodeId, Duration banDuration, String reason) {
		// no-op
	}
}
