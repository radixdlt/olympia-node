package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.network.RadixNodeStatus;
import com.radixdlt.client.core.network.reducers.RadixNodeState;

/**
 * A connection status filter that filters out inactive peers
 */
public class ConnectionAliveFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixNodeState peerState) {
		return peerState.getStatus() == RadixNodeStatus.CONNECTED || peerState.getStatus() == RadixNodeStatus.CONNECTING;
	}
}
