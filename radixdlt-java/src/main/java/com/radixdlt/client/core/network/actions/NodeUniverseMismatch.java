package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNode;
import com.radixdlt.client.core.network.RadixNodeAction;

/**
 * An action which represents a node was found which does not match the universe expected
 */
public class NodeUniverseMismatch implements RadixNodeAction {
	private final RadixNode node;
 	private final RadixUniverseConfig expected;
	private final RadixUniverseConfig actual;

	public NodeUniverseMismatch(RadixNode node, RadixUniverseConfig expected, RadixUniverseConfig actual) {
		this.node = node;
		this.expected = expected;
		this.actual = actual;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	public RadixUniverseConfig getActual() {
		return actual;
	}

	public RadixUniverseConfig getExpected() {
		return expected;
	}

	@Override
	public String toString() {
		return "NODE_UNIVERSE_MISMATCH " + node + " expected: " + expected + " but was " + actual;
	}
}
