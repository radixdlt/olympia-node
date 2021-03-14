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

import com.radixdlt.atom.Atom;
import com.radixdlt.client.serialization.Serialize;
import com.radixdlt.serialization.DsonOutput;

public class TestUtils {
	private TestUtils() {
	}

	private static final int HEXDUMP_LINESIZE = 0x20;

	/**
	 * Dump the JSON representation of the binary generated for hashing
	 *
	 * @param atom The atom
	 */
	public static void dumpJsonForHash(Atom atom) {
		System.out.println(Serialize.getInstance().toJson(atom, DsonOutput.Output.HASH));
	}

	/**
	 * Dump the DSON representation of the binary generated for hashing
	 *
	 * @param atom The atom
	 */
	public static void dumpDsonForHash(Atom atom) {
		hexdump(atom.toDson());
	}

	/**
	 * Useful method for discovering why things went wrong - outputs
	 * a hexdump to {@code System.out}.
	 *
	 * @param bytes bytes to dump
	 */
	public static void hexdump(byte[] bytes) {
		for (int index = 0; index < bytes.length; index += HEXDUMP_LINESIZE) {
			int thisLen = Math.min(HEXDUMP_LINESIZE, bytes.length - index);
			System.out.format("%04X:", index);
			int ofs = 0;
			for (; ofs < thisLen; ++ofs) {
				if (ofs == HEXDUMP_LINESIZE / 2) {
					System.out.format("-%02X", bytes[index + ofs] & 0xFF);
				} else {
					System.out.format(" %02X", bytes[index + ofs] & 0xFF);
				}
			}
			while (ofs < HEXDUMP_LINESIZE) {
				System.out.print("   ");
				ofs += 1;
			}
			System.out.print("  |");
			for (ofs = 0; ofs < thisLen; ++ofs) {
				System.out.print(toPrintable(bytes[index + ofs]));
			}
			while (ofs < HEXDUMP_LINESIZE) {
				System.out.print(' ');
				ofs += 1;
			}
			System.out.println('|');
		}
	}

	private static char toPrintable(byte b) {
		if (b >= 0x20 && b < 0x7F) {
			return (char) b;
		}
		return '.';
	}
}
