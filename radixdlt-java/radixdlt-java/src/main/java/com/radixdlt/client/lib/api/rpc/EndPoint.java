package com.radixdlt.client.lib.api.rpc;

import static com.radixdlt.client.lib.api.rpc.PortSelector.PRIMARY;
import static com.radixdlt.client.lib.api.rpc.PortSelector.SECONDARY;

public enum EndPoint {
	ACCOUNTS("/account", PRIMARY),
	TOKENS("/token", PRIMARY),
	CONSTRUCTION("/construction", PRIMARY),
	TRANSACTIONS("/transaction", PRIMARY),
	NETWORK("/network", PRIMARY),

	TRANSACTIONS_NODE("/transactions", SECONDARY),
	SYSTEM_NODE("/system", SECONDARY),
	ACCOUNT_NODE("/account", SECONDARY),
	VALIDATION_NODE("/validation", SECONDARY);

	private final String path;
	private final PortSelector portSelector;

	EndPoint(String path, PortSelector portSelector) {
		this.path = path;
		this.portSelector = portSelector;
	}

	public String path() {
		return path;
	}

	public PortSelector portSelector() {
		return portSelector;
	}
}

