package com.radixdlt.client.core.ledger.selector;

import com.radixdlt.client.core.network.RadixPeer;

import java.util.List;
import java.util.Random;

/**
 * A simple randomized selector that returns an arbitrary peer out of the given list
 */
public class RandomSelector implements RadixPeerSelector {
	private Random rng = new Random();

	@Override
	public RadixPeer apply(List<RadixPeer> radixPeers) {
		return radixPeers.get(rng.nextInt(radixPeers.size()));
	}
}
