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

package org.radix.utils;

import java.math.BigInteger;
import java.util.Arrays;

import org.junit.Test;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Basic unit tests for {@link Int128}.
 *
 * @author msandiford
 */
public class Int128Test {

	// Range of numbers to be tested at extremes for Integer and Long.
	private static final int TEST_RANGE = 1_000_000;

	/**
	 * Exhaustively test construction and accessors from {@link Number}
	 * for all {@code short} values, from {@link Short.MIN_VALUE} up to
	 * and including {@link Short.MAX_VALUE}.
	 */
	@Test
	public void testShortValues() {
		for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; ++i) {
			short s = (short) i;
			Int128 int128 = Int128.from(s);
			assertEquals(s, int128.shortValue());
			assertEquals(s, int128.intValue());
			assertEquals(s, int128.longValue());
			// delta of 0.0 is correct, as integer doubles in this range are exact.
			// 16-bit short significand is smaller that 53 bit double significand.
			assertEquals(s, int128.doubleValue(), 0.0);
		}
	}

	/**
	 * Test construction and accessors from {@link Number} for two ranges
	 * of {@code int} values from {@code Integer.MIN_VALUE} through to
	 * {@code Integer.MIN_VALUE + TEST_RANGE}, and also from
	 * {@code Integer.MAX_VALUE} down to
	 * {@code Integer.MAX_VALUE - TEST_RANGE}.
	 */
	@Test
	public void testIntValue() {
		// Here we will just assume that testing some values near the
		// extremes of the range will suffice.
		for (int i = 0; i <= TEST_RANGE; ++i) {
			int ii = Integer.MIN_VALUE + i;
			Int128 int128 = Int128.from(ii);
			assertEquals(ii, int128.intValue());
			assertEquals(ii, int128.longValue());
			// delta of 0.0 is correct, as integer doubles in this range are exact.
			// 32-bit int significand is smaller that 53 bit double significand.
			assertEquals(ii, int128.doubleValue(), 0.0);
		}
		for (int i = 0; i <= TEST_RANGE; ++i) {
			int ii = Integer.MAX_VALUE - i;
			Int128 int128 = Int128.from(ii);
			assertEquals(ii, int128.intValue());
			assertEquals(ii, int128.longValue());
			// delta of 0.0 is correct, as integer doubles in this range are exact.
			// 32-bit int significand is smaller that 53 bit double significand.
			assertEquals(ii, int128.doubleValue(), 0.0);
		}
	}

	/**
	 * Test construction and accessors from {@link Number} for two ranges
	 * of {@code long} values from {@code Long.MIN_VALUE} through to
	 * {@code Long.MIN_VALUE + TEST_RANGE}, and also from
	 * {@code Long.MAX_VALUE} down to
	 * {@code Long.MAX_VALUE - TEST_RANGE}.
	 */
	@Test
	public void testLongValue() {
		// Here we will just assume that testing some values near the
		// extremes of the range will suffice.
		for (int i = 0; i < TEST_RANGE; ++i) {
			long l = Long.MIN_VALUE + i;
			Int128 int128 = Int128.from(l);
			assertEquals(l, int128.longValue());
			// delta of 0.0 is correct, as integer doubles in this range are exact.
			// Note that due to limited range of TEST_RANGE, the lower
			// (64 - ceil(log2(TEST_RANGE))) bits are always zero for the abs value.
			assertEquals(l, int128.doubleValue(), 0.0);
			assertEquals(new BigInteger(int128.toByteArray()).longValue(), int128.longValue());
		}
		for (int i = 0; i < TEST_RANGE; ++i) {
			long l = Long.MAX_VALUE - i;
			Int128 int128 = Int128.from(l);
			assertEquals(l, int128.longValue());
			// delta of 0.0 is correct, as integer doubles in this range are exact.
			// Note that due to limited range of TEST_RANGE, the lower
			// (64 - ceil(log2(TEST_RANGE))) bits are always zero.
			assertEquals(l, int128.doubleValue(), 0.0);
			assertEquals(new BigInteger(int128.toByteArray()).longValue(), int128.longValue());
		}
	}

	/**
	 * Test longValue() method.
	 */
	@Test
	public void testLongValueMethod() {
		// Here we will just assume that testing some values near the
		// extremes of the range will suffice.
		for (int i = 0; i < TEST_RANGE; ++i) {
			long h = Long.MIN_VALUE + i;
			Int128 int128 = Int128.from(h, 0L);
			assertEquals(new BigInteger(int128.toByteArray()).longValue(), int128.longValue());
		}
		for (int i = 0; i < TEST_RANGE; ++i) {
			long h = Long.MAX_VALUE - i;
			Int128 int128 = Int128.from(h, 0L);
			assertEquals(new BigInteger(int128.toByteArray()).longValue(), int128.longValue());
		}
	}

	/**
	 * Test doubleValue() special cases not tested above.
	 */
	@Test
	public void testDoubleValue() {
		Int128 minus2To64 = Int128.from(0xFFFF_FFFF_FFFF_FFFFL, 0x0000_0000_0000_0000L);
		double d1 = minus2To64.doubleValue();
		double twoTo64TimesMinus1 = Math.pow(2.0, 64.0) * -1.0;
		assertEquals(twoTo64TimesMinus1, d1, 0.0);

		Int128 addend = Int128.from(0x0000_0000_0000_0000L, 0x7FFF_FFFF_FFFF_FFFFL);
		double d2 = minus2To64.add(addend).doubleValue();
		double twoTo63TimesMinus1 = Math.pow(2.0, 63.0) * -1.0;
		assertEquals(twoTo63TimesMinus1, d2, 0.0); // Lowest bit will be dropped

		Int128 int128As2To64Minus1 = Int128.from(0x0000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFFFL);
		double d3 = int128As2To64Minus1.doubleValue();
		assertEquals(Math.pow(2.0, 64.0), d3, 0.0); // Rounded up

		assertEquals(-1.0, Int128.MINUS_ONE.doubleValue(), 0.0);
		assertEquals(0.0, Int128.ZERO.doubleValue(), 0.0);
	}

	/**
	 * Test to ensure that addition is functioning properly, including
	 * negative numbers, and overflow between the two underlying long
	 * values.
	 */
	@Test
	public void testAddition() {
		// Some basics
		// 0 + 1 = 1
		assertEquals(1L, Int128.ZERO.add(Int128.ONE).longValue());
		// 1 + 1 = 2
		assertEquals(2L, Int128.ONE.add(Int128.ONE).longValue());
		// 1 + (-1) = -1
		assertEquals(-1L, Int128.ZERO.add(Int128.MINUS_ONE).longValue());
		// (-1) + 1 = 0
		assertEquals(0L, Int128.MINUS_ONE.add(Int128.ONE).longValue());
		// (-1) + (-1) = -2
		assertEquals(-2L, Int128.MINUS_ONE.add(Int128.MINUS_ONE).longValue());

		// Test adding with carry.
		Int128 carry1 = Int128.from(0x0000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFFFL).add(Int128.ONE);
		assertEquals(0x0000_0000_0000_0001L, carry1.getHigh());
		assertEquals(0x0000_0000_0000_0000L, carry1.getLow());
		Int128 carry2 = Int128.ZERO.add(Int128.MINUS_ONE);
		assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getHigh());
		assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getLow());
	}

	/**
	 * Test to ensure that subtraction is functioning properly, including
	 * negative numbers, and overflow between the two underlying long
	 * values.
	 */
	@Test
	public void testSubtraction() {
		// Some basics
		// 0 - 1 = -1
		assertEquals(-1L, Int128.ZERO.subtract(Int128.ONE).longValue());
		// 1 - 1 = 0
		assertEquals(0L, Int128.ONE.subtract(Int128.ONE).longValue());
		// 1 - (-1) = 1
		assertEquals(1L, Int128.ZERO.subtract(Int128.MINUS_ONE).longValue());
		// (-1) - 1 = -2
		assertEquals(-2L, Int128.MINUS_ONE.subtract(Int128.ONE).longValue());
		// (-1) - (-1) = 0
		assertEquals(0L, Int128.MINUS_ONE.subtract(Int128.MINUS_ONE).longValue());

		// Test adding with carry.
		Int128 carry1 = Int128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000L).subtract(Int128.ONE);
		assertEquals(0x0000_0000_0000_0000L, carry1.getHigh());
		assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry1.getLow());
		Int128 carry2 = Int128.ZERO.subtract(Int128.ONE);
		assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getHigh());
		assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getLow());
	}

	/**
	 * Test multiplication.
	 */
	@Test
	public void testMultiplication() {
		// Some basics
		assertEquals(Int128.ZERO, Int128.ZERO.multiply(Int128.ONE));
		assertEquals(Int128.ZERO, Int128.ONE.multiply(Int128.ZERO));
		assertEquals(Int128.MINUS_ONE, Int128.ONE.multiply(Int128.MINUS_ONE));
		assertEquals(Int128.MINUS_ONE, Int128.MINUS_ONE.multiply(Int128.ONE));
		assertEquals(Int128.ONE, Int128.MINUS_ONE.multiply(Int128.MINUS_ONE));

		// Some values in the long range
		assertEquals(Int128.from(12345678L * 13L), Int128.from(12345678L).multiply(Int128.from(13L)));
		assertEquals(Int128.from(-12345678L * 13L), Int128.from(-12345678L).multiply(Int128.from(13L)));
		assertEquals(Int128.from(-12345678L * -13L), Int128.from(-12345678L).multiply(Int128.from(-13L)));
	}

	/**
	 * Test division.
	 */
	@Test
	public void testDivision() {
		// Some basics
		assertEquals(Int128.ZERO, Int128.ZERO.divide(Int128.ONE));
		assertEquals(Int128.MINUS_ONE, Int128.ONE.divide(Int128.MINUS_ONE));
		assertEquals(Int128.MINUS_ONE, Int128.MINUS_ONE.divide(Int128.ONE));
		assertEquals(Int128.ONE, Int128.MINUS_ONE.divide(Int128.MINUS_ONE));

		// Some values in the long range
		assertEquals(Int128.from(12345678L / 13L), Int128.from(12345678L).divide(Int128.from(13L)));
		assertEquals(Int128.from(-12345678L / 13L), Int128.from(-12345678L).divide(Int128.from(13L)));
		assertEquals(Int128.from(-12345678L / -13L), Int128.from(-12345678L).divide(Int128.from(-13L)));
	}

	/**
	 * Test remainder.
	 */
	@Test
	public void testRemainder() {
		// Some basics
		Int128 two = Int128.from(2);
		assertEquals(Int128.ZERO, Int128.ZERO.remainder(Int128.ONE));
		assertEquals(Int128.ZERO, Int128.ONE.remainder(Int128.MINUS_ONE));
		assertEquals(Int128.ZERO, Int128.MINUS_ONE.remainder(Int128.ONE));
		assertEquals(Int128.MINUS_ONE, Int128.MINUS_ONE.remainder(two));
		assertEquals(Int128.ONE, Int128.ONE.remainder(two));

		// Some values in the long range
		assertEquals(Int128.from(12345678L % 13L), Int128.from(12345678L).remainder(Int128.from(13L)));
		assertEquals(Int128.from(-12345678L % 13L), Int128.from(-12345678L).remainder(Int128.from(13L)));
		assertEquals(Int128.from(12345678L % -13L), Int128.from(12345678L).remainder(Int128.from(-13L)));
		assertEquals(Int128.from(-12345678L % -13L), Int128.from(-12345678L).remainder(Int128.from(-13L)));
	}

	/**
	 * Test {@link Number#doubleValue()} for values that exceed the range
	 * of a single {@code long} value.
	 */
	@Test
	public void testLargeDouble() {
		Int128 i64 = Int128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000L);
		Int128 i65 = i64.add(i64);
		Int128 i66 = i65.add(i65);

		// These should all be exact for binary double irep.
		double d64 = Math.pow(2.0, 64.0);
		double d65 = Math.pow(2.0, 65.0);
		double d66 = Math.pow(2.0, 66.0);

		assertEquals(d64, i64.doubleValue(), 0.0);
		assertEquals(d65, i65.doubleValue(), 0.0);
		assertEquals(d66, i66.doubleValue(), 0.0);

		assertEquals(d64 + d65, i64.add(i65).doubleValue(), 0.0);
		assertEquals(d64 + d66, i64.add(i66).doubleValue(), 0.0);
		assertEquals(d65 + d66, i65.add(i66).doubleValue(), 0.0);
		assertEquals(d64 + d65 + d66, i64.add(i65).add(i66).doubleValue(), 0.0);

		// Test negative numbers too
		i64 = Int128.ZERO.subtract(i64);
		i65 = Int128.ZERO.subtract(i65);
		i66 = Int128.ZERO.subtract(i66);
		d64 = 0.0 - d64;
		d65 = 0.0 - d65;
		d66 = 0.0 - d66;

		assertEquals(d64, i64.doubleValue(), 0.0);
		assertEquals(d65, i65.doubleValue(), 0.0);
		assertEquals(d66, i66.doubleValue(), 0.0);

		assertEquals(d64 + d65, i64.add(i65).doubleValue(), 0.0);
		assertEquals(d64 + d66, i64.add(i66).doubleValue(), 0.0);
		assertEquals(d65 + d66, i65.add(i66).doubleValue(), 0.0);
		assertEquals(d64 + d65 + d66, i64.add(i65).add(i66).doubleValue(), 0.0);
	}

	/**
	 * Basic tests for comparisons.
	 */
	@Test
	public void testCompare() {
		assertThat(Int128.ZERO, comparesEqualTo(Int128.ZERO));
		assertThat(Int128.ZERO, lessThan(Int128.ONE));
		assertThat(Int128.ZERO, greaterThan(Int128.MINUS_ONE));
		assertThat(Int128.MINUS_ONE, lessThan(Int128.ONE));
		assertThat(Int128.ONE, greaterThan(Int128.MINUS_ONE));

		Int128 i63 = Int128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
		Int128 i64 = i63.add(i63);
		Int128 i65 = i64.add(i64);
		assertThat(i64, greaterThan(i63)); // In case something has gone horribly wrong.
		assertThat(i64.add(i63), greaterThan(i64));
		assertThat(i65, greaterThan(i64.add(i63)));
	}

	/**
	 * Basic tests for equals(...) method.
	 */
	@Test
	public void testEquals() {
		assertNotEquals(Int128.ZERO, null); // Nothing should be equal to null
		assertEquals(Int128.ZERO, Int128.ZERO); // Same object check
		Int128 i63a = Int128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
		Int128 i63b = Int128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
		assertEquals(i63a, i63b);
		assertNotEquals(i63a, i63a.add(i63b));
		assertNotEquals(i63a, Int128.ZERO);
	}

	/**
	 * Tests for numberOfLeadingZeros().
	 */
	@Test
	public void testNumberOfLeadingZeros() {
		assertEquals(Int128.SIZE, Int128.ZERO.numberOfLeadingZeros());
		assertEquals(Int128.SIZE - 1, Int128.ONE.numberOfLeadingZeros());
		assertEquals(0, Int128.MINUS_ONE.numberOfLeadingZeros());
		assertEquals(63, Int128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000).numberOfLeadingZeros());
	}

	/**
	 * Tests for bit operations.
	 */
	@Test
	public void testBitOperations() {
		// Use bit positions in both high and low word for tests
		Int128 b0 = Int128.ONE;
		Int128 b64 = Int128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000);
		// Basic sanity checks to make sure nothing is horribly wrong
		assertThat(b64, greaterThan(b0));
		assertThat(b0, greaterThan(Int128.ZERO));
		assertThat(b64, greaterThan(Int128.ZERO));

		// Now for the real tests
		assertEquals(b0, Int128.ZERO.or(b0));
		assertEquals(b0, b0.or(b0));
		assertEquals(b64, Int128.ZERO.or(b64));
		assertEquals(b64, b64.or(b64));
		assertEquals(Int128.MINUS_ONE, Int128.ZERO.or(Int128.MINUS_ONE));
		assertEquals(Int128.MINUS_ONE, Int128.MINUS_ONE.or(Int128.MINUS_ONE));

		assertEquals(b0, Int128.MINUS_ONE.and(b0));
		assertEquals(b64, Int128.MINUS_ONE.and(b64));
		assertEquals(Int128.ZERO, Int128.MINUS_ONE.and(Int128.ZERO));

		assertEquals(b0, Int128.ZERO.xor(b0));
		assertEquals(Int128.ZERO, b0.xor(b0));
		assertEquals(b64, Int128.ZERO.xor(b64));
		assertEquals(Int128.ZERO, b64.xor(b64));
		assertEquals(Int128.MINUS_ONE, Int128.ZERO.xor(Int128.MINUS_ONE));
		assertEquals(Int128.ZERO, Int128.MINUS_ONE.xor(Int128.MINUS_ONE));
	}

	/**
	 * Test creation from byte array.
	 */
	@Test
	public void testCreateFromByteArray() {
		byte[] m1 = {
			-1
		};
		byte[] p1 = {
			1
		};
		byte[] bytesArray = new byte[Int128.BYTES];
		Arrays.fill(bytesArray, (byte) 0);
		bytesArray[Int128.BYTES - 1] = 1;
		Int128 m1AsInt128 = Int128.from(m1);
		Int128 p1AsInt128 = Int128.from(p1);
		Int128 bytesArrayAsInt128 = Int128.from(bytesArray);

		assertEquals(Int128.MINUS_ONE, m1AsInt128);   // Sign extension happened correctly
		assertEquals(Int128.ONE, p1AsInt128);         // Zero fill happened correctly
		assertEquals(Int128.ONE, bytesArrayAsInt128); // Correct size array OK
	}

	/**
	 * Test toByteArray(...) methods.
	 */
	@Test
	public void testToByteArray() {
		Int128 bitPattern = Int128.from(0x0001_0203_0405_0607L, 0x0809_0A0B_0C0D_0E0FL);
		byte[] bytes2 = new byte[Int128.BYTES * 3];
		Arrays.fill(bytes2, (byte) -1);

		// Make sure we got the value in big-endian order
		byte[] bytes = bitPattern.toByteArray();
		for (int i = 0; i < Int128.BYTES; ++i) {
			assertEquals(i, bytes[i]);
		}

		bitPattern.toByteArray(bytes2, Int128.BYTES);
		// Make sure we didn't overwrite bytes outside our range
		for (int i = 0; i < Int128.BYTES; ++i) {
			assertEquals(-1, bytes2[i]);
			assertEquals(-1, bytes2[i + Int128.BYTES * 2]);
		}
		// Make sure we got the value in big-endian order
		for (int i = 0; i < Int128.BYTES; ++i) {
			assertEquals(i, bytes2[Int128.BYTES + i]);
		}
	}

	/**
	 * Test shiftLeft and shiftRight.
	 */
	@Test
	public void testShifts() {
		// Basic cases, left shift
		assertEquals(Int128.ZERO, Int128.ZERO.shiftLeft());
		// Zero extend on left
		assertEquals(Int128.from(-1L, -2L), Int128.MINUS_ONE.shiftLeft());
		assertEquals(Int128.from(0L, 2L), Int128.ONE.shiftLeft());
		// Make sure bit crosses word boundary correctly
		assertEquals(Int128.from(1L, 0L), Int128.from(0L, 1L << (Long.SIZE - 1)).shiftLeft());

		// Basic cases, right shift
		assertEquals(Int128.ZERO, Int128.ZERO.shiftRight());
		// Sign extend on right
		assertEquals(Int128.MINUS_ONE, Int128.MINUS_ONE.shiftRight());
		assertEquals(Int128.ZERO, Int128.ONE.shiftRight());
		assertEquals(Int128.ONE, Int128.from(0L, 2L).shiftRight());
		// Make sure bit crosses word boundary correctly
		assertEquals(Int128.from(0L, 0x8000_0000_0000_0000L), Int128.from(1L, 0L).shiftRight());

		// Basic cases, div2
		assertEquals(Int128.ZERO, Int128.ZERO.div2());
		// Round towards zero
		assertEquals(Int128.ZERO, Int128.MINUS_ONE.div2());
		assertEquals(Int128.ZERO, Int128.ONE.div2());
		assertEquals(Int128.ONE, Int128.from(2L).div2());
		assertEquals(Int128.ONE, Int128.from(3L).div2());
		assertEquals(Int128.MINUS_ONE, Int128.from(-2L).div2());
		assertEquals(Int128.MINUS_ONE, Int128.from(-3L).div2());
		// Make sure bit crosses word boundary correctly
		assertEquals(Int128.from(0L, 0x8000_0000_0000_0000L), Int128.from(1L, 0L).div2());
	}

	/**
	 * Test abs().
	 */
	@Test
	public void testAbs() {
		// abs(0) == 0
		assertEquals(Int128.ZERO, Int128.ZERO.abs());
		// abs(1) == 1
		assertEquals(Int128.ONE, Int128.ONE.abs());
		// abs(-1) == 1
		assertEquals(Int128.ONE, Int128.MINUS_ONE.abs());

		// Pathological case
		assertEquals(
			Int128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L),
			Int128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L).abs()
		);

		// Intermediate word overflow case
		assertEquals(
			Int128.from(1L, 0L),
			Int128.from(0xFFFF_FFFF_FFFF_FFFFL, 0x0000_0000_0000_0000L).abs()
		);
	}

	/**
	 * Test isEven(), isOdd(), isZero() predicates.
	 */
	@Test
	public void testPredicates() {
		// Basic tests for odd/even
		assertTrue(Int128.ONE.isOdd());
		assertFalse(Int128.ONE.isEven());
		assertTrue(Int128.MINUS_ONE.isOdd());
		assertFalse(Int128.MINUS_ONE.isEven());
		Int128 two = Int128.ONE.add(Int128.ONE);
		assertFalse(two.isOdd());
		assertTrue(two.isEven());
		Int128 minusTwo = Int128.MINUS_ONE.add(Int128.MINUS_ONE);
		assertFalse(minusTwo.isOdd());
		assertTrue(minusTwo.isEven());

		assertFalse(Int128.ONE.isZero());
		assertFalse(Int128.MINUS_ONE.isZero());
		assertTrue(Int128.ZERO.isZero());
	}

	/**
	 * Test toString() method.
	 */
	@Test
	public void testToString() {
		// Some basics
		assertEquals("0", Int128.ZERO.toString());
		assertEquals("1", Int128.ONE.toString());
		assertEquals("-1", Int128.MINUS_ONE.toString());
		assertEquals("10", Int128.TEN.toString());

		assertEquals("12345678", Int128.from(12345678L).toString());
		assertEquals("-12345678", Int128.from(-12345678L).toString());
		Int128 maxPositive = Int128.from(0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL);
		assertEquals(new BigInteger(maxPositive.toString()).toString(), maxPositive.toString());
		Int128 maxNegative = Int128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L);
		assertEquals(new BigInteger(maxNegative.toString()).toString(), maxNegative.toString());
	}

	/**
	 * Test materialising {@code Int128} values from {@code String} values.
	 */
	@Test
	public void testFromString() {
		testRoundTrip("0");
		testRoundTrip("-1");
		testRoundTrip("123456789");
		testRoundTrip("-123456789");
		testRoundTrip("123456789123456789");
		testRoundTrip("-123456789123456789");
		testRoundTrip("123456789123456789123456789123456789");
		testRoundTrip("-123456789123456789123456789123456789");
		assertEquals(
			Int128.from(0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL),
			Int128.from(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE).toString())
		);
		assertEquals(
			Int128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L),
			Int128.from(BigInteger.ONE.shiftLeft(127).negate().toString())
		);
	}

	private static void testRoundTrip(String s) {
		assertEquals(s, Int128.from(s).toString());
	}
}
