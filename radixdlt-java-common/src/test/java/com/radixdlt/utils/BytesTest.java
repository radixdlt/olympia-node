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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BytesTest {

	/**
	 * Test that partial array compares for equality work.
	 */
	@Test
	public void testArrayEquals() {
		byte[] array1 = {
			0, 1, 2, 3, 4, 5, 6, 7, 8,  9
		};
		byte[] array2 = {
			1, 2, 3, 4, 5, 6, 7, 8, 9, 10
		};

		// Parts of array the same at non-matching indices
		assertTrue(Bytes.arrayEquals(array1, 1, 5, array2, 0, 5));
		// Mismatch due to length
		assertFalse(Bytes.arrayEquals(array1, 1, 5, array2, 0, 4));
		// Mismatch due to non-equal data
		assertFalse(Bytes.arrayEquals(array1, 0, 5, array2, 0, 5));
	}

	/**
	 * Test that hash codes for partial arrays are equal.
	 */
	@Test
	public void testHashCode() {
		byte[] array1 = {
			0, 1, 2, 3, 4, 5, 6, 7, 8,  9
		};
		byte[] array2 = {
			1, 2, 3, 4, 5, 6, 7, 8, 9, 10
		};

		for (int i = 0; i < 10; ++i) {
			assertEquals(Bytes.hashCode(array1, 1, i), Bytes.hashCode(array2, 0, i));
		}
	}

	/**
	 * Test conversion from byte to hex string.
	 */
	@Test
	public void testToHexStringByte() {
		for (int i = 0; i < 0x100; ++i) {
			String base = String.format("%02x", i);
			String convert = Bytes.toHexString((byte) i);
			assertEquals(base, convert);
		}
	}

	/**
	 * Test conversion from array of bytes to hex string.
	 */
	@Test
	public void testToHexStringByteArray() {
		byte[] bytes = new byte[256];
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 256; ++i) {
			bytes[i] = (byte) i;
			sb.append(String.format("%02x", i));
		}
		String base = sb.toString();
		assertEquals(base, Bytes.toHexString(bytes));
	}

	/**
	 * Test conversion from partial array to hex string.
	 */
	@Test
	public void testToHexStringPartialByteArray() {
		byte[] bytes = new byte[256];
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 256; ++i) {
			bytes[i] = (byte) i;
			sb.append(String.format("%02x", i));
		}
		String base = sb.toString();

		// Offset 0, varying lengths
		for (int i = 0; i < 256; ++i) {
			assertEquals(base.substring(0, i * 2), Bytes.toHexString(bytes, 0, i));
		}
		// Varying offsets, fixed length
		for (int i = 0; i < 200; ++i) {
			assertEquals(base.substring(i * 2, i * 2 + 20), Bytes.toHexString(bytes, i, 10));
		}
	}

	/**
	 * Various test cases for conversion of string to byte array.
	 */
	@Test
	public void testFromHexString() {
		// Single byte
		byte[] expected1 = {
			(byte) 0xAA
		};
		assertArrayEquals(expected1, Bytes.fromHexString("AA"));
		assertArrayEquals(expected1, Bytes.fromHexString("aa"));
		assertArrayEquals(expected1, Bytes.fromHexString("aA"));
		// two bytes
		byte[] expected2 = {
			(byte) 0xAB, (byte) 0xCD
		};
		assertArrayEquals(expected2, Bytes.fromHexString("ABCD"));
		assertArrayEquals(expected2, Bytes.fromHexString("abcd"));
		// two and a half bytes
		byte[] expected3 = {
			(byte) 0x0A, (byte) 0xBC, (byte) 0xDE
		};
		assertArrayEquals(expected3, Bytes.fromHexString("ABCDE"));
		assertArrayEquals(expected3, Bytes.fromHexString("abcde"));
		// four bytes
		byte[] expected4 = {
			(byte) 0xAB, (byte) 0xCD, (byte) 0xEF
		};
		assertArrayEquals(expected4, Bytes.fromHexString("ABCDEF"));
		assertArrayEquals(expected4, Bytes.fromHexString("abcdef"));
		// eight bytes
		byte[] expected8 = {
			0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF
		};
		assertArrayEquals(expected8, Bytes.fromHexString("0123456789ABCDEF"));
		assertArrayEquals(expected8, Bytes.fromHexString("0123456789abcdef"));

		// Invalid characters
		assertThatThrownBy(() -> Bytes.fromHexString("!")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Bytes.fromHexString(":")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Bytes.fromHexString("[")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Bytes.fromHexString("~")).isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * Various test cases for trimLeadingZeros.
	 */
	@Test
	public void testTrimLeadingZeros() {
		assertEquals(null, Bytes.trimLeadingZeros(null)); // Null -> noop

		byte[] emptyBytes = new byte[0];
		assertEquals(emptyBytes, Bytes.trimLeadingZeros(emptyBytes)); // Empty -> noop

		// All size 1 byte arrays -> noop
		for (int i = 0; i < 255; ++i) {
			byte[] oneByte = new byte[1];
			oneByte[0] = (byte) i;
			assertEquals(oneByte, Bytes.trimLeadingZeros(oneByte));
		}

		// All size 2 byte arrays -> trimmed
		for (int i = 0; i < 255; ++i) {
			byte[] oneByte = new byte[1];
			byte[] twoBytes = new byte[2];
			oneByte[0] = (byte) i;
			twoBytes[1] = (byte) i;
			assertArrayEquals(oneByte, Bytes.trimLeadingZeros(twoBytes));
		}

		byte[] noLeadingZeros = new byte[] {
			1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		assertEquals(noLeadingZeros, Bytes.trimLeadingZeros(noLeadingZeros));

		byte[] singleZero = new byte[1];
		byte[] severalZeros = new byte[10];
		assertArrayEquals(singleZero, Bytes.trimLeadingZeros(severalZeros));

		byte[] zeroRemoved = new byte[] {
			1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		byte[] withZero = new byte[] {
			0, 1, 2, 3, 4, 5, 6, 7, 8, 9
		};
		assertArrayEquals(zeroRemoved, Bytes.trimLeadingZeros(withZero));
	}
}
