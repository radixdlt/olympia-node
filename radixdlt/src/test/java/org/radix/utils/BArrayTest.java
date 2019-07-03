package org.radix.utils;

import org.junit.Test;

import static org.junit.Assert.*;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.radix.utils.BArray;

public class BArrayTest {

	@Test
	public void equalsContract() {
	    EqualsVerifier.forClass(BArray.class).verify();
	}

	@SuppressWarnings("unused")
	@Test(expected=IllegalArgumentException.class)
	public void testIntConstructorFail() {
		new BArray(-1);
	}

	@Test
	public void testIntConstructor() {
		BArray test0 = new BArray(0);
		assertEquals(0, test0.size());
		BArray test1 = new BArray(1);
		assertEquals(64, test1.size());
		BArray test64 = new BArray(64);
		assertEquals(64, test64.size());
		BArray test65 = new BArray(65);
		assertEquals(128, test65.size());
	}

	@Test
	public void testCopyOf() {
		BArray testArray1 = new BArray(128);
		for (int i = 0; i < testArray1.size(); i += 2) {
			testArray1.set(i);
		}
		for (int i = 0; i < testArray1.size(); i += 2) {
			assertTrue(testArray1.get(i));
		}
		for (int i = 1; i < testArray1.size(); i += 2) {
			assertFalse(testArray1.get(i));
		}
		BArray testArray2 = BArray.copyOf(testArray1);
		assertEquals(testArray1, testArray2);
	}

	@Test(expected=NullPointerException.class)
	public void testValueOfFail() {
		BArray.valueOf(null);
	}

	@Test
	public void testValueOf() {
		BArray testArray1 = new BArray(128);
		for (int i = 0; i < testArray1.size(); i += 2) {
			testArray1.set(i);
		}
		for (int i = 0; i < testArray1.size(); i += 2) {
			assertTrue(testArray1.get(i));
		}
		for (int i = 1; i < testArray1.size(); i += 2) {
			assertFalse(testArray1.get(i));
		}
		BArray testArray2 = BArray.valueOf(testArray1.toByteArray());
		assertEquals(testArray1, testArray2);

		byte[] testByte = new byte[] { 0x55 };
		BArray testArray3 = BArray.valueOf(testByte);
		assertEquals(1, testArray3.bitArray.length);
		assertEquals(0x5500_0000_0000_0000L, testArray3.bitArray[0]);

		byte[] testBytes = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07 };
		BArray testArray4 = BArray.valueOf(testBytes);
		assertEquals(1, testArray4.bitArray.length);
		assertEquals(0x0102_0304_0506_0700L, testArray4.bitArray[0]);
	}

	@Test
	public void testClearAll() {
		BArray testArray1 = new BArray(128);
		for (int i = 0; i < testArray1.size(); i += 2) {
			testArray1.set(i);
		}
		for (int i = 0; i < testArray1.size(); i += 2) {
			assertTrue(testArray1.get(i));
		}
		for (int i = 1; i < testArray1.size(); i += 2) {
			assertFalse(testArray1.get(i));
		}
		testArray1.clear();
		for (int i = 0; i < testArray1.size(); i += 1) {
			assertFalse(testArray1.get(i));
		}
	}

	@Test
	public void testClear() {
		BArray testArray1 = new BArray(128);
		for (int i = 0; i < testArray1.size(); i += 1) {
			testArray1.set(i);
		}
		for (int i = 1; i < testArray1.size(); i += 2) {
			testArray1.clear(i);
		}
		for (int i = 0; i < testArray1.size(); i += 2) {
			assertTrue(testArray1.get(i));
		}
		for (int i = 1; i < testArray1.size(); i += 2) {
			assertFalse(testArray1.get(i));
		}
	}

	@Test
	public void testCopyBits() {
		BArray evenBits = new BArray(1 << 20);
		for (int i = 0; i < evenBits.size(); i += 2) {
			evenBits.set(i);
		}

		// Copy the complete thing
		BArray test1 = new BArray(1 << 20);
		test1.copyBits(evenBits, 0, test1.size());
		assertEquals(evenBits, test1);

		// One byte from one into other
		BArray test2 = new BArray(1 << 20);
		test2.copyBits(evenBits, 0, 8);
		assertEquals(0xAA00_0000_0000_0000L, test2.bitArray[0]);

		// Copy with differing stride
		BArray test3 = new BArray(128);
		test3.copyBits(evenBits, 8, 120);
		assertEquals(0x00AA_AAAA_AAAA_AAAAL, test3.bitArray[0]);
		assertEquals(0xAAAA_AAAA_AAAA_AAAAL, test3.bitArray[1]);
	}

	@Test
	public void testReadBits() {
		long[] testArray = new long[] { 0x8000_0000_0000_0000L, 0x5555_5555_5555_5555L, 0xAAAA_AAAA_AAAA_AAAAL };
		assertEquals(1L, BArray.readBits(testArray, 0, 63, 1));
		for (int i = 0; i < 64; ++i) {
			assertEquals((~i) & 1L, BArray.readBits(testArray, 1, i, 1));
		}
		for (int i = 0; i < 64; ++i) {
			assertEquals(i & 1L, BArray.readBits(testArray, 2, i, 1));
		}
		for (int i = 3; i < 64; i += 4) {
			assertEquals(0x5L, BArray.readBits(testArray, 1, i, 4));
		}
		for (int i = 4; i < 64; i += 4) {
			assertEquals(0xAL, BArray.readBits(testArray, 1, i, 4));
		}
		for (int i = 3; i < 64; i += 4) {
			assertEquals(0xAL, BArray.readBits(testArray, 2, i, 4));
		}
		for (int i = 4; i < 64; i += 4) {
			assertEquals(0x5L, BArray.readBits(testArray, 2, i, 4));
		}
		assertEquals(0x8000_0000_0000_0000L, BArray.readBits(testArray, 0, 63, 64));
		assertEquals(0x5555_5555_5555_5555L, BArray.readBits(testArray, 1, 63, 64));
		assertEquals(0xAAAA_AAAA_AAAA_AAAAL, BArray.readBits(testArray, 2, 63, 64));
	}

	@Test
	public void testWriteBits() {
		long[] testArray = new long[2];
		BArray.writeBits(testArray, 0, 3, 4, 0x5L);
		assertEquals(5L, testArray[0]);
		assertEquals(0L, testArray[1]);

		BArray.writeBits(testArray, 0, 63, 4, 0x5L);
		assertEquals(0x5000_0000_0000_0005L, testArray[0]);
		assertEquals(0L, testArray[1]);

		BArray.writeBits(testArray, 1, 63, 64, 0xAAAA_AAAA_AAAA_AAAAL);
		assertEquals(0x5000_0000_0000_0005L, testArray[0]);
		assertEquals(0xAAAA_AAAA_AAAA_AAAAL, testArray[1]);
	}
}
