package com.radixdlt.store.tree;

import org.bouncycastle.util.Arrays;

import java.nio.ByteBuffer;
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
	public static Integer nibbleToInteger(byte[] nibble) {
		return ByteBuffer.wrap(nibble).getInt();
	}

	public static String toByteString(Integer keyNibble) {
		return Integer.toBinaryString(keyNibble);
	}

}
