package com.radixdlt.consensus;

/**
 * Manages safety of the protocol.
 * TODO: Add storage of private key of node here
 */
public final class SafetyRules {
	public Vote vote(Vertex vertex) {
		return new Vote(vertex.getRound(), vertex.hashCode());
	}
}
