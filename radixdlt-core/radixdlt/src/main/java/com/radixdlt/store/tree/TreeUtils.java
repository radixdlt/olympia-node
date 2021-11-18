package com.radixdlt.store.tree;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TreeUtils {

	public static byte[] getFirstNibble(byte[] hash) {
		byte[] nibble = new byte[4];
		for(int i=0;i<=4;++i) {
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

	final static int EVEN_SIZE = 8;
	final static int ODD_SIZE = 4;

	public static byte[] applyPrefix(byte[] rawKey, byte[] oddPrefix, byte[] evenPrefix) {
		var keyLength = rawKey.length;
		byte[] prefixed;
		if (keyLength % 2 == 0) {
			ByteBuffer bb = ByteBuffer.allocate(1 + keyLength);
			bb.put(evenPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		} else {
			ByteBuffer bb = ByteBuffer.allocate(1 + keyLength);
			bb.put(oddPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		}
		return prefixed;
	}
/*
	public static byte[] applyPrefix(byte[] rawKey, byte[] oddPrefix, byte[] evenPrefix) {
		var keyLength = rawKey.length;
		byte[] prefixed;
		if (keyLength % 8 == 0) {
			ByteBuffer bb = ByteBuffer.allocate(EVEN_SIZE + keyLength);
			bb.put(evenPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		} else if (keyLength % 4 == 0) {
			ByteBuffer bb = ByteBuffer.allocate(ODD_SIZE + keyLength);
			bb.put(oddPrefix);
			bb.put(rawKey);
			prefixed = bb.array();
		} else {
			throw new IllegalArgumentException("Key length must be divisible by 4");
		}
		return prefixed;
	}
*/
	public static Integer nibbleToInteger(byte[] nibble) {
		return ByteBuffer.wrap(nibble).getInt();
	}

	public static String toByteString(Integer keyNibble) {
		return Integer.toBinaryString(keyNibble);
	}

}
