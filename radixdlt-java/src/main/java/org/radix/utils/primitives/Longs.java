package org.radix.utils.primitives;

import java.util.Objects;

/**
 * Utilities for manipulating primitive {@code long} values.
 */
public final class Longs {

	private Longs() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Create a byte array of length {@link Long#BYTES}, and
	 * populate it with {@code value} in big-endian order.
	 *
	 * @param value The value to convert
	 * @return The resultant byte array.
	 */
	public static byte[] toByteArray(long value) {
		return copyTo(value, new byte[Long.BYTES], 0);
	}

	/**
	 * Copy the byte value of {@code value} into {@code bytes}
	 * starting at {@code offset}.  A total of {@link Long#BYTES}
	 * will be written to {@code bytes}.
	 *
	 * @param value  The value to convert
	 * @param bytes  The array to write the value into
	 * @param offset The offset at which to write the value
	 * @return The value of {@code bytes}
	 */
	public static byte[] copyTo(long value, byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");
	    for (int i = offset + Long.BYTES - 1; i >= offset; i--) {
	    	bytes[i] = (byte) (value & 0xFFL);
	    	value >>>= 8;
	    }
	    return bytes;
	}

	/**
	 * Exactly equivalent to {@code fromByteArray(bytes, 0)}.
	 *
	 * @param bytes The byte array to decode to a long
	 * @return The decoded long value
	 * @see #fromByteArray(byte[], int)
	 */
	public static long fromByteArray(byte[] bytes) {
		return fromByteArray(bytes, 0);
	}

	/**
	 * Decode a long from array {@code bytes} at {@code offset}.
	 * Bytes from array {@code bytes[offset]} up to and including
	 * {@code bytes[offset + Long.BYTES - 1]} will be read from
	 * array {@code bytes}.
	 *
	 * @param bytes The byte array to decode to a long
	 * @param offset The offset within the array to start decoding
	 * @return The decoded long value
	 */
	public static long fromByteArray(byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "bytes is null for 'long' conversion");
		long value = 0;
		for (int b = 0; b < Long.BYTES; b++) {
			value <<= 8;
			value |= bytes[offset + b] & 0xFFL;
		}
		return value;
	}

	/**
	 * Assemble a {@code long} value from it's component bytes.
	 *
	 * @param b0 Most significant byte
	 * @param b1 Next most significant byte
	 * @param b2 &hellip;
	 * @param b3 &hellip;
	 * @param b4 &hellip;
	 * @param b5 &hellip;
	 * @param b6 Next least significant byte
	 * @param b7 Least significant byte
	 * @return The {@code long} value represented by the arguments.
	 */
	public static long fromBytes(byte b0, byte b1, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7) {
		return (b0 & 0xFFL) << 56 | (b1 & 0xFFL) << 48 | (b2 & 0xFFL) << 40 | (b3 & 0xFFL) << 32
			 | (b4 & 0xFFL) << 24 | (b5 & 0xFFL) << 16 | (b6 & 0xFFL) <<  8 | (b7 & 0xFFL);
	}

	/**
	 * Compares two {@code long} values numerically treating the values
	 * as unsigned.
	 *
	 * @param  x the first {@code long} to compare
	 * @param  y the second {@code long} to compare
	 * @return the value {@code 0} if {@code x == y}; a value less
	 *         than {@code 0} if {@code x < y} as unsigned values; and
	 *         a value greater than {@code 0} if {@code x > y} as
	 *         unsigned values
	 */
	public static int compareUnsigned(long x, long y) {
		return Long.compare(x + Long.MIN_VALUE, y + Long.MIN_VALUE);
	}
}
