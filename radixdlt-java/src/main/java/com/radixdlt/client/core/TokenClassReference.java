package com.radixdlt.client.core;

import com.google.gson.annotations.SerializedName;
import com.radixdlt.client.core.address.EUID;

public class TokenClassReference {

	@SerializedName("token_id")
	private final EUID token;
	private final EUID revision;

	public TokenClassReference(EUID token, EUID revision) {
		this.token = token;
		this.revision = revision;
	}

	public EUID getToken() {
		return token;
	}
}
