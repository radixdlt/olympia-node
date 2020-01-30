/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.utils;

/**
 * Some useful string handling methods, currently mostly here
 * for performance reasons.
 */
public final class Strings {

	private Strings() {
		throw new IllegalStateException("Can't construct");
	}

	/**
	 * Brutally convert a string to a sequence of ASCII bytes by
	 * discarding all but the lower 7 bits of each {@code char} in
	 * {@code s}.
	 * <p>
	 * The primary purpose of this method is to implement a speedy
	 * converter between strings and bytes where characters are
	 * known to be limited to the ASCII character set.
	 * <p>
	 * Note that the output will consume exactly {@code s.length()}
	 * bytes.
	 *
	 * @param s The string to convert.
	 * @param bytes The buffer to place the converted bytes into.
	 * @param ofs   The offset within the buffer to place the converted bytes.
	 * @return The offset within the buffer immediately past the converted string.
	 */
	public static int toAsciiBytes(String s, byte[] bytes, int ofs) {
		for (int i = 0; i < s.length(); ++i) {
			bytes[ofs++] = (byte) (s.charAt(i) & 0x7F);
		}
		return ofs;
	}

	/**
	 * Convert a sequence of ASCII bytes into a string.  Note that
	 * no bounds checking is performed on the incoming bytes &#x2014;
	 * the upper bit is silently discarded.
	 *
	 * @param bytes  The buffer to convert to a string.
	 * @param ofs    The offset within the buffer to start conversion.
	 * @param len    The number of bytes to convert.
	 * @return A {@link String} of length {@code len}.
	 */
	public static String fromAsciiBytes(byte[] bytes, int ofs, int len) {
		char[] chars = new char[len];
		for (int i = 0; i < len; ++i) {
			chars[i] = (char) (bytes[ofs + i] & 0x7F);
		}
		return new String(chars);
	}
}
