package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixNode;

import java.util.List;
import java.util.Random;

/**
 * A simple randomized selector that returns an arbitrary peer out of the given list
 */
public class RandomSelector implements RadixPeerSelector {
	private Random rng = new Random();

	@Override
	public RadixNode apply(List<RadixNode> radixNodes) {
		return radixNodes.get(rng.nextInt(radixNodes.size()));
	}
}
