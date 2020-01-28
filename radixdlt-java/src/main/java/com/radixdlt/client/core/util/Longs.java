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
