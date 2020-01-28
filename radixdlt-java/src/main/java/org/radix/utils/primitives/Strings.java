/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package org.radix.utils.primitives;

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
	 * no bounds checking is performed on the incoming bytes;
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
