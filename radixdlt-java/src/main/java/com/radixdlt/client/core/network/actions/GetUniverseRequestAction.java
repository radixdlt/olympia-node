package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

public class GetUniverseRequestAction implements JsonRpcMethodAction {

	private final RadixNode node;

	private GetUniverseRequestAction(RadixNode node) {
		Objects.requireNonNull(node);
		this.node = node;
	}

	public static GetUniverseRequestAction of(RadixNode node) {
		return new GetUniverseRequestAction(node);
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "GET_UNIVERSE_REQUEST " + node;
	}
}
