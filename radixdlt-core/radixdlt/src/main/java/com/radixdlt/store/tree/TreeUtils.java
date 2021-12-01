package com.radixdlt.store.tree;

import org.bouncycastle.util.Arrays;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TreeUtils {

	private TreeUtils() { }

	public static byte[] getFirstNibble(byte[] hash) {
		byte[] nibble = new byte[4];
		for (int i = 0; i <= 4; ++i) {
			nibble[i] = hash[i];
		}
		// should we set it in a cache field for re-use?
		return nibble;
	}

	public static String toHexString(byte[] byteBuffer) {
		return IntStream.range(0, byteBuffer.length)
			.map(i -> byteBuffer[i] & 0xff)
			.mapToObj(b -> String.format("%02x", b))
			.collect(Collectors.joining());
	}

	public static byte[] applyPrefix(byte[] rawKey, int oddPrefix, int evenPrefix) {
		var keyLength = rawKey.length;
		if (keyLength % 2 == 0) {
			return Arrays.concatenate(new byte[] {(byte) evenPrefix, 0}, rawKey);
		} else {
			return Arrays.concatenate(new byte[] {(byte) oddPrefix}, rawKey);
		}
	}

	/**
	 * Convert an array of nibbles to a byte array. Starting from the beginning of the array, it converts each pair
	 * of nibbles into a byte. The first nibble is the 4 most significant bits and the second one the 4 least.
	 * <br/>
	 * Examples: <br/>
	 *	[0, 1, 0, 2] becomes [1, 2] <br/>
	 *  [1, 1, 2, 2] becomes [17, 34] <br/>
	 *
	 * @param nibbles array to be converted
	 * @return byte array of the converted nibbles
	 * @throws NullPointerException if the nibbles array is null
	 * @throws IllegalArgumentException if nibbles array has odd length
	 */
	public static byte[] fromNibblesToBytes(byte[] nibbles) {
		Objects.requireNonNull(nibbles);
		if (nibbles.length % 2 != 0) {
			throw new IllegalArgumentException("Nibbles array must have even length.");
		}
		byte[] bytes = new byte[0];
		for (int i = 0; i < nibbles.length; i += 2) {
			byte b = (byte) ((byte) (nibbles[i] << 4) + (nibbles[i + 1]));
			bytes = Arrays.append(bytes, b);
		}
		return bytes;
	}

	public static Integer nibbleToInteger(byte[] nibble) {
		return ByteBuffer.wrap(nibble).getInt();
	}

	public static String toByteString(Integer keyNibble) {
		return Integer.toBinaryString(keyNibble);
	}

}
