package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeer;

import java.util.List;

/**
 * A selector that always returns the first peer
 */
public class GetFirstSelector implements RadixPeerSelector {
	@Override
	public RadixPeer apply(List<RadixPeer> radixPeers) {
		return radixPeers.get(0);
	}
}
