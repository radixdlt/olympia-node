package com.radixdlt.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Class holding commonly use constant values.
 *
 * @author msandiford
 */
public final class RadixConstants {

	private RadixConstants() {
		throw new IllegalStateException("Can't construct.");
	}

	/** Default {@link Charset} to use when converting to/from bytes. */
	public static final Charset STANDARD_CHARSET = StandardCharsets.UTF_8;
}
