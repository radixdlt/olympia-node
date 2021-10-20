package com.radixdlt.api.routing;

public enum BasePath {
	ARCHIVE("/archive"),
	CONSTRUCTION("/construction"),
	TRANSACTIONS("/transactions"),
	SYSTEM("/system"),
	ACCOUNT("/account"),
	VALIDATION("/validation"),
	METRICS("/metrics"),
	CHAOS("/chaos"),
	HEALTH("/health"),
	FAUCET("/faucet"),
	DEVELOPER("/developer"),
	VERSION("/version"),
	UNIVERSE("/universe.json");

	private final String path;
	
	BasePath(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}
}

