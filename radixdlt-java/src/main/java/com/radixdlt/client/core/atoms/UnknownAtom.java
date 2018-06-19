package com.radixdlt.client.core.atoms;

import com.google.gson.JsonObject;

public class UnknownAtom extends Atom {

	private final JsonObject representation;

	public UnknownAtom(JsonObject object) {
		super();
		this.representation = object.deepCopy();
	}
}
