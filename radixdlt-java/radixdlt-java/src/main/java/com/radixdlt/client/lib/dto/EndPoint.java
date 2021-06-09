package com.radixdlt.client.lib.dto;

enum EndPoint {
	ARCHIVE("/archive"),
	CONSTRUCTION("/construction"),
	SYSTEM("/system"),
	ACCOUNT("/account"),
	VALIDATION("/validation");

	final String path;

	EndPoint(String path) {
		this.path = path;
	}

	public String path() {
		return path;
	}
}

