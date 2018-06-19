package com.radixdlt.client.core.util;

import java.math.BigInteger;

public class Base58 {

	private static final char[] b58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

	private static final int[] r58 = new int[256];

	static {
		for (int i = 0; i < 256; ++i) {
			r58[i] = -1;
		}
		for (int i = 0; i < b58.length; ++i) {
			r58[b58[i]] = i;
		}
	}

	private Base58() {
	}


	// Encodes the specified byte array into a String using the Base58 encoding scheme
	public static String toBase58(byte[] b) {
		if (b.length == 0) {
			return "";
		}

		int lz = 0;
		while (lz < b.length && b[lz] == 0) {
			++lz;
		}

		StringBuffer s = new StringBuffer();
		BigInteger n = new BigInteger(1, b);
		while (n.compareTo(BigInteger.ZERO) > 0) {
			BigInteger[] r = n.divideAndRemainder(BigInteger.valueOf(58));
			n = r[0];
			char digit = b58[r[1].intValue()];
			s.append(digit);
		}
		while (lz > 0) {
			--lz;
			s.append("1");
		}
		return s.reverse().toString();
	}

	// Decodes the specified Base58 encoded String to its byte array representation
	public static byte[] fromBase58(String s) {
		try {
			boolean leading = true;
			int lz = 0;
			BigInteger b = BigInteger.ZERO;
			for (char c : s.toCharArray()) {
				if (leading && c == '1') {
					++lz;
				} else {
					leading = false;
					b = b.multiply(BigInteger.valueOf(58));
					b = b.add(BigInteger.valueOf(r58[c]));
				}
			}
			byte[] encoded = b.toByteArray();
			if (encoded[0] == 0) {
				if (lz > 0) {
					--lz;
				} else {
					byte[] e = new byte[encoded.length - 1];
					System.arraycopy(encoded, 1, e, 0, e.length);
					encoded = e;
				}
			}
			byte[] result = new byte[encoded.length + lz];
			System.arraycopy(encoded, 0, result, lz, encoded.length);

			return result;
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new IllegalArgumentException("Invalid character in address");
		} catch (Exception e) {
			throw new IllegalArgumentException(e);
		}
	}
}
