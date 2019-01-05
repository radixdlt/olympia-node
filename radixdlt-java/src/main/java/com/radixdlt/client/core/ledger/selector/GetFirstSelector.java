package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixNode;

import java.util.List;

/**
 * A selector that always returns the first peer
 */
public class GetFirstSelector implements RadixPeerSelector {
	@Override
	public RadixNode apply(List<RadixNode> radixNodes) {
		return radixNodes.get(0);
	}
}
