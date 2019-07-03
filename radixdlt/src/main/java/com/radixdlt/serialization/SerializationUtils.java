package com.radixdlt.serialization;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.radixdlt.common.EUID;

import java.nio.charset.StandardCharsets;

/**
 * Collection of Serialization-related utilities
 */
public class SerializationUtils {
	private static final HashFunction murmur3_128 = Hashing.murmur3_128();

	private SerializationUtils() {
		throw new IllegalStateException("Cannot instantiate.");
	}

	public static EUID stringToNumericID(String id) {
		HashCode h = murmur3_128.hashBytes(id.getBytes(StandardCharsets.UTF_8));
		return new EUID(h.asBytes());
	}
}
