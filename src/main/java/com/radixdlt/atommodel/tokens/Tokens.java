package com.radixdlt.atommodel.tokens;

public final class Tokens {

	private Tokens() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Returns the short code of the native asset of the network this node is a part of.
	 *
	 * @return The short code of the native asset.
	 */
	public static String getNativeTokenShortCode() {
		return "XRD";
	}
}
