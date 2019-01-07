package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.RadixNodeState;

/**
 * A connection status filter that filters out inactive peers
 */
public class ConnectionAliveFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixNodeState peerState) {
		return peerState.getStatus() == WebSocketStatus.CONNECTED || peerState.getStatus() == WebSocketStatus.CONNECTING;
	}
}
