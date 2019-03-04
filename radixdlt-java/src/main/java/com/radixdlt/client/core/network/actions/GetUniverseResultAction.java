package com.radixdlt.client.core.network.actions;

import com.radixdlt.client.core.address.RadixUniverseConfig;
import com.radixdlt.client.core.network.RadixNode;
import java.util.Objects;

public class GetUniverseResultAction implements JsonRpcResultAction<RadixUniverseConfig> {
	private final RadixNode node;
	private final RadixUniverseConfig config;

	private GetUniverseResultAction(RadixNode node, RadixUniverseConfig config) {
		Objects.requireNonNull(node);
		Objects.requireNonNull(config);

		this.node = node;
		this.config = config;
	}

	public static GetUniverseResultAction of(RadixNode node, RadixUniverseConfig config) {
		return new GetUniverseResultAction(node, config);
	}

	@Override
	public RadixUniverseConfig getResult() {
		return config;
	}

	@Override
	public RadixNode getNode() {
		return node;
	}

	@Override
	public String toString() {
		return "GET_UNIVERSE_RESPONSE " + node + " " + config;
	}
}
