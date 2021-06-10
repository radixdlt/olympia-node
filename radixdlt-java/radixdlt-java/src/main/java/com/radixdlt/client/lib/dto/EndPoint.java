package com.radixdlt.client.lib.dto;

import static com.radixdlt.client.lib.dto.PortSelector.PRIMARY;
import static com.radixdlt.client.lib.dto.PortSelector.SECONDARY;

public enum EndPoint {
	ARCHIVE("/archive", PRIMARY),
	CONSTRUCTION("/construction", PRIMARY),
	SYSTEM("/system", SECONDARY),
	ACCOUNT("/account", SECONDARY),
	VALIDATION("/validation", SECONDARY);

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

