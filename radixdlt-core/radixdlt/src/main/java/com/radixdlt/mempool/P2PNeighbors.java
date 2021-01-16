package com.radixdlt.mempool;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.bft.BFTNode;

import java.util.Objects;

public class P2PNeighbors {
	private final ImmutableSet<BFTNode> neighbors;
	private P2PNeighbors(ImmutableSet<BFTNode> neighbors) {
		this.neighbors = neighbors;
	}

	public ImmutableSet<BFTNode> nodes() {
		return neighbors;
	}

	public static P2PNeighbors create(ImmutableSet<BFTNode> neighbors) {
		Objects.requireNonNull(neighbors);
		return new P2PNeighbors(neighbors);
	}
}
