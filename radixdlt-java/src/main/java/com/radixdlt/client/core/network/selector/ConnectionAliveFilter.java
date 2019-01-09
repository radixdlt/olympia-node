package com.radixdlt.client.core.network.selector;

import com.radixdlt.client.core.network.websocket.WebSocketStatus;
import com.radixdlt.client.core.network.RadixNodeState;

/**
 * A connection status filter that filters out inactive peers
 */
public class ConnectionAliveFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixNodeState nodeState) {
		return nodeState.getStatus() == WebSocketStatus.CONNECTED || nodeState.getStatus() == WebSocketStatus.CONNECTING;
	}
}
