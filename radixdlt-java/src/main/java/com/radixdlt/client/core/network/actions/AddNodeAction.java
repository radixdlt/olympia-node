package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNodeAction;
import com.radixdlt.client.core.network.RadixPeer;

public class AddNodeAction implements RadixNodeAction {
	private final RadixPeer node;
	public AddNodeAction(RadixPeer node) {
		this.node = node;
	}

	@Override
	public RadixPeer getNode() {
		return node;
	}

	public String toString() {
		return "ADD_NODE " + node;
	}
}
