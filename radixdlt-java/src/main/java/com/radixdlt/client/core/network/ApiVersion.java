package com.radixdlt.client.core.network;

/**
 * Simple API Version used for syncing between server/client
 */
public class ApiVersion {
	private final Integer version;

	private ApiVersion(Integer version) {
		this.version = version;
	}

	public Integer getVersion() {
		return version;
	}
}
