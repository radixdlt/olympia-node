package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeer;
import com.radixdlt.client.core.network.WebSocketClient;

public class CompatibleApiVersionFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixPeer radixPeer, WebSocketClient.RadixClientStatus radixClientStatus) {
		return radixPeer.getRadixClient().checkAPIVersion().onErrorReturnItem(false).blockingGet();
	}
}
