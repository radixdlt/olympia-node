package com.radixdlt.atommodel.tokens;

import java.util.Objects;

import com.radixdlt.common.EUID;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.RadixConstants;

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

	/**
	 * Returns the ID of the native asset of the network this node is a part of.
	 *
	 * @return The ID of the native asset.
	 */
	public static EUID getNativeTokenEUID() {
		return shortCodeToEUID(getNativeTokenShortCode());
	}

	/**
	 * Convert an asset short code ("ISO" code) into an ID.
	 *
	 * @param isoCode The asset short code to convert.
	 * @return The ID of the asset short code.
	 */
	public static EUID shortCodeToEUID(String isoCode) {
		Objects.requireNonNull(isoCode);
		return new EUID(Hash.hash256(isoCode.getBytes(RadixConstants.STANDARD_CHARSET)));
	}
}
