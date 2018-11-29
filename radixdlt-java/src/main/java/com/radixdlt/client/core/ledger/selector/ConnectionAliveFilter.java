package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.WebSocketClient;

/**
 * A connection status filter that filters out inactive peers
 */
public class ConnectionAliveFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixPeer radixPeer, WebSocketClient.RadixClientStatus radixClientStatus) {
		return radixClientStatus == WebSocketClient.RadixClientStatus.OPEN || radixClientStatus == WebSocketClient.RadixClientStatus.CONNECTING;
	}
}
