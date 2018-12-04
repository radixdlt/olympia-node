package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixClientStatus;
import com.radixdlt.client.core.network.RadixPeerState;

/**
 * A connection status filter that filters out inactive peers
 */
public class ConnectionAliveFilter implements RadixPeerFilter {
	@Override
	public boolean test(RadixPeerState peerState) {
		return peerState.status == RadixClientStatus.OPEN || peerState.status == RadixClientStatus.CONNECTING;
	}
}
