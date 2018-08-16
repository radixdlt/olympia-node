package com.radixdlt.client.core.util;

import java.util.Objects;

public final class Longs {
	private Longs() {
		throw new IllegalStateException("Can't construct");
	}

	public static byte[] toByteArray(long value) {
		return toByteArray(value, new byte[Long.BYTES], 0);
	}

	public static byte[] toByteArray(long value, byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");

		if (offset + Long.BYTES > bytes.length) {
			throw new IllegalArgumentException("bytes is too short for 'long' conversion");
		}

	    for (int i = Long.BYTES - 1; i >= 0; i--) {
	    	bytes[offset + i] = (byte) (value & 0xffL);
	    	value >>>= 8;
	    }

	    return bytes;
	}

	public static long fromByteArray(byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");
		int length = Math.min(bytes.length - offset, Long.BYTES);
		if (length <= 0) {
			throw new IllegalArgumentException("no bytes for 'long' conversion");
		}

		long value = 0;

		for (int b = 0; b < length; b++) {
			value <<= 8;
			value |= bytes[offset + b] & 0xFFL;
		}

		return value;
	}
}
