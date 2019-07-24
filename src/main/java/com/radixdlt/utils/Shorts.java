package com.radixdlt.utils;

public final class Shorts {
	private Shorts() {
		throw new IllegalStateException("Can't construct");
	}

	public static byte[] toByteArray(short value) {
		return new byte[] {
			(byte) (value >> 8),
			(byte) value
		};
	}

	public static short fromByteArray(byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException("Array is null or has zero length for 'int' conversion");
		}

		int length = Math.min(bytes.length, Short.BYTES);
		short value = 0;

		for (int b = bytes.length - length; b < bytes.length; b++) {
			value |= (bytes[b] & 0xFFL);

			if (b < bytes.length - 1) {
				value = (short) (value << 8);
			}
		}

		return value;
	}
}
