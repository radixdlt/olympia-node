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
import static org.junit.Assert.fail;

/**
 * Basic unit tests for {@link UInt256}.
 *
 * @author msandiford
 */
public class UInt256Test {

	// Range of numbers to be tested at extremes for Integer and Long.
	private static final int TEST_RANGE = 1_000_000;

	/**
	 * Exhaustively test construction for all non-negative {@code short}
	 * values, from {@code 0} up to and including {@link Short.MAX_VALUE}.
	 */
	@Test
	public void when_constructing_int256_from_short_values__values_compare_equal() {
		for (int i = 0; i <= Short.MAX_VALUE; ++i) {
			short s = (short) i;
			UInt256 int256 = UInt256.from(s);
			assertEqualToLong(s, int256);
		}
	}

	/**
	 * Test construction for two ranges of {@code int} values from {@code 0}
	 * through to {@code TEST_RANGE}, and also from {@code Integer.MAX_VALUE}
	 * down to {@code Integer.MAX_VALUE - TEST_RANGE}.
	 */
	@Test
	public void when_constructing_int256_from_int_values__values_compare_equal() {
		// Here we will just assume that testing some values near the
		// extremes of the range will suffice.
		for (int i = 0; i <= TEST_RANGE; ++i) {
			UInt256 int256 = UInt256.from(i);
			assertEqualToLong(i, int256);
		}
		for (int i = 0; i <= TEST_RANGE; ++i) {
			int ii = Integer.MAX_VALUE - i;
			UInt256 int256 = UInt256.from(ii);
			assertEqualToLong(ii, int256);
		}
	}

	/**
	 * Test construction for two ranges of {@code long} values from {@code 0}
	 * through to {@code TEST_RANGE}, and also from {@code Long.MAX_VALUE}
	 * down to {@code Long.MAX_VALUE - TEST_RANGE}.
	 */
	@Test
	public void when_constructing_int256_from_long_values__accessors_compare_equal() {
		// Here we will just assume that testing some values near the
		// extremes of the range will suffice.
		for (int i = 0; i < TEST_RANGE; ++i) {
			long l = i;
			UInt256 int256 = UInt256.from(l);
			assertEqualToLong(l, int256);
		}
		for (int i = 0; i < TEST_RANGE; ++i) {
			long l = Long.MAX_VALUE - i;
			UInt256 int256 = UInt256.from(l);
			assertEqualToLong(l, int256);
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_constructing_int256_from_long_negative_value__an_exception_is_thrown() {
		UInt256.from(-1L);
		fail();
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_constructing_int256_from_int128_negative_value__an_exception_is_thrown() {
		UInt256.from(Int128.MINUS_ONE);
		fail();
	}

	@Test
	public void when_performing_basic_addition__the_correct_values_are_returned() {
		// Some basics
		// 0 + 1 = 1
		assertEqualToLong(1L, UInt256.ZERO.add(UInt256.ONE));
		// 1 + 1 = 2
		assertEqualToLong(2L, UInt256.ONE.add(UInt256.ONE));
		// max + 1 = 0 (overflow)
		assertEqualToLong(0L, UInt256.MAX_VALUE.add(UInt256.ONE));
		// 1 + max = 0 (overflow)
		assertEqualToLong(0L, UInt256.ONE.add(UInt256.MAX_VALUE));
	}

	@Test
	public void when_performing_addition_overflowing_between_words__the_correct_values_are_returned() {
		// Test adding with carry.
		UInt256 carry1 = UInt256.from(Int128.ZERO, Int128.MINUS_ONE).add(UInt256.ONE);
		assertEquals(Int128.ONE, carry1.getHigh());
		assertEquals(Int128.ZERO, carry1.getLow());
	}

	@Test
	public void when_performing_basic_subtraction__the_correct_values_are_returned() {
		// Some basics
		// 1 - 1 = 0
		assertEqualToLong(0L, UInt256.ONE.subtract(UInt256.ONE));
		// 2 - 1 = 1
		assertEqualToLong(1L, UInt256.TWO.subtract(UInt256.ONE));
		// 0 - 1 = max (underflow)
		assertEquals(UInt256.MAX_VALUE, UInt256.ZERO.subtract(UInt256.ONE));
		// 0 - max = 1 (underflow)
		assertEqualToLong(1L, UInt256.ZERO.subtract(UInt256.MAX_VALUE));
	}

	@Test
	public void when_incrementing_int256__the_correct_values_are_returned() {
		assertEquals(UInt256.ONE, UInt256.ZERO.increment());
		assertEquals(UInt256.ZERO, UInt256.MAX_VALUE.increment()); // Internal and full overflow
	}

	@Test
	public void when_decrementing_int256__the_correct_values_are_returned() {
		assertEquals(UInt256.ZERO, UInt256.ONE.decrement());
		assertEquals(UInt256.MAX_VALUE, UInt256.ZERO.decrement()); // Internal and full overflow
	}

	@Test
	public void when_performing_subtraction_underflowing_between_words__the_correct_value_is_returned() {
		// Test adding with carry.
		UInt256 carry1 = UInt256.from(Int128.ONE, Int128.ZERO).subtract(UInt256.ONE);
		assertEquals(Int128.ZERO, carry1.getHigh());
		assertEquals(Int128.MINUS_ONE, carry1.getLow());
		UInt256 carry2 = UInt256.ZERO.subtract(UInt256.ONE); // underflow
		assertEquals(Int128.MINUS_ONE, carry2.getHigh());
		assertEquals(Int128.MINUS_ONE, carry2.getLow());
	}

	@Test
	public void when_multiplying_two_values__the_correct_value_is_returned() {
		// Some basics
		assertEquals(UInt256.ZERO, UInt256.ZERO.multiply(UInt256.ZERO));
		assertEquals(UInt256.ZERO, UInt256.ZERO.multiply(UInt256.ONE));
		assertEquals(UInt256.ZERO, UInt256.ONE.multiply(UInt256.ZERO));
		assertEquals(UInt256.ONE, UInt256.ONE.multiply(UInt256.ONE));

		// Some values in the long range
		assertEquals(UInt256.from(12345678L * 13L), UInt256.from(12345678L).multiply(UInt256.from(13L)));
	}

	@Test
	public void when_dividing_one_value_by_another__the_correct_value_is_returned() {
		// Some basics
		assertEquals(UInt256.ZERO, UInt256.ZERO.divide(UInt256.ONE));
		assertEquals(UInt256.ONE, UInt256.ONE.divide(UInt256.ONE));

		// Some values in the long range
		assertEquals(UInt256.from(12345678L / 13L), UInt256.from(12345678L).divide(UInt256.from(13L)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_dividing_by_zero__an_exception_is_thrown() {
		UInt256.ONE.divide(UInt256.ZERO);
		fail();
	}

	@Test
	public void when_computing_the_remainder_of_dividing_one_value_by_another__the_correct_value_is_returned() {
		// Some basics
		assertEquals(UInt256.ZERO, UInt256.ZERO.remainder(UInt256.ONE));
		assertEquals(UInt256.ZERO, UInt256.ONE.remainder(UInt256.ONE));
		assertEquals(UInt256.ONE, UInt256.ONE.remainder(UInt256.TWO));

		// Some values in the long range
		assertEquals(UInt256.from(12345678L % 13L), UInt256.from(12345678L).remainder(UInt256.from(13L)));
	}

	@Test(expected = IllegalArgumentException.class)
	public void when_computing_the_remainder_of_dividing_by_zero__an_exception_is_thrown() {
		UInt256.ONE.remainder(UInt256.ZERO);
		fail();
	}

	@Test
	public void when_comparing_int256_values_using_compareTo__the_correct_value_is_returned() {
		assertThat(UInt256.ZERO, comparesEqualTo(UInt256.ZERO));
		assertThat(UInt256.ZERO, lessThan(UInt256.ONE));
		assertThat(UInt256.ZERO, lessThan(UInt256.MAX_VALUE));
		assertThat(UInt256.ONE, greaterThan(UInt256.ZERO));
		assertThat(UInt256.MAX_VALUE, greaterThan(UInt256.ZERO));

		UInt256 i127 = UInt256.from(Int128.ZERO, Int128.MIN_VALUE);
		UInt256 i128 = i127.add(i127);
		UInt256 i129 = i128.add(i128);
		assertThat(i128, greaterThan(i127)); // In case something has gone horribly wrong.
		assertThat(i128.add(i127), greaterThan(i128));
		assertThat(i129, greaterThan(i128.add(i127)));
	}

	@Test
	public void when_comparing_int256_values_using_equals__the_correct_value_is_returned() {
		assertNotEquals(UInt256.ZERO, null); // Nothing should be equal to null
		assertEquals(UInt256.ZERO, UInt256.ZERO); // Same object check
		UInt256 i127a = UInt256.from(Int128.ZERO, Int128.MIN_VALUE);
		UInt256 i127b = UInt256.from(Int128.ZERO, Int128.MIN_VALUE);
		assertEquals(i127a, i127b);
		assertNotEquals(i127a, i127b.add(i127b));
		assertNotEquals(i127a, UInt256.ZERO);
	}

	@Test
	public void when_calculating_leading_zeros__the_correct_value_is_returned() {
		assertEquals(UInt256.SIZE, UInt256.ZERO.numberOfLeadingZeros());
		assertEquals(UInt256.SIZE - 1, UInt256.ONE.numberOfLeadingZeros());
		assertEquals(0, UInt256.MAX_VALUE.numberOfLeadingZeros());
		assertEquals(UInt256.SIZE / 2 - 1, UInt256.from(Int128.ONE, Int128.ZERO).numberOfLeadingZeros());
	}

	@Test
	public void when_binary_oring_two_values__the_correct_value_is_returned() {
		// Use bit positions in both high and low word for tests
		UInt256 b0 = UInt256.ONE;
		UInt256 b128 = UInt256.from(Int128.ONE, Int128.ZERO);
		// Basic sanity checks to make sure nothing is horribly wrong
		assertThat(b128, greaterThan(b0));
		assertThat(b0, greaterThan(UInt256.ZERO));
		assertThat(b128, greaterThan(UInt256.ZERO));

		// Now for the real tests
		assertEquals(b0, UInt256.ZERO.or(b0));
		assertEquals(b0, b0.or(b0));
		assertEquals(b128, UInt256.ZERO.or(b128));
		assertEquals(b128, b128.or(b128));
		assertEquals(UInt256.MAX_VALUE, UInt256.ZERO.or(UInt256.MAX_VALUE));
		assertEquals(UInt256.MAX_VALUE, UInt256.MAX_VALUE.or(UInt256.MAX_VALUE));
	}

	@Test
	public void when_binary_anding_two_values__the_correct_value_is_returned() {
		// Use bit positions in both high and low word for tests
		UInt256 b0 = UInt256.ONE;
		UInt256 b128 = UInt256.from(Int128.ONE, Int128.ZERO);
		assertEquals(b0, UInt256.MAX_VALUE.and(b0));
		assertEquals(b128, UInt256.MAX_VALUE.and(b128));
		assertEquals(UInt256.ZERO, UInt256.MAX_VALUE.and(UInt256.ZERO));
	}

	@Test
	public void when_binary_xoring_two_values__the_correct_value_is_returned() {
		// Use bit positions in both high and low word for tests
		UInt256 b0 = UInt256.ONE;
		UInt256 b128 = UInt256.from(Int128.ONE, Int128.ZERO);
		assertEquals(b0, UInt256.ZERO.xor(b0));
		assertEquals(UInt256.ZERO, b0.xor(b0));
		assertEquals(b128, UInt256.ZERO.xor(b128));
		assertEquals(UInt256.ZERO, b128.xor(b128));
		assertEquals(UInt256.MAX_VALUE, UInt256.ZERO.xor(UInt256.MAX_VALUE));
		assertEquals(UInt256.ZERO, UInt256.MAX_VALUE.xor(UInt256.MAX_VALUE));
	}

	@Test
	public void when_creating_int256_from_byte_array__the_correct_value_is_created() {
		byte[] m1 = {
			-1
		};
		byte[] p1 = {
			1
		};
		byte[] bytesArray = new byte[UInt256.BYTES];
		Arrays.fill(bytesArray, (byte) 0);
		bytesArray[UInt256.BYTES - 1] = 1;
		UInt256 m1AsUInt256 = UInt256.from(m1);
		UInt256 p1AsUInt256 = UInt256.from(p1);
		UInt256 bytesArrayAsUInt256 = UInt256.from(bytesArray);

		assertEquals(UInt256.from(255), m1AsUInt256);   // Sign extension did not happen
		assertEquals(UInt256.ONE, p1AsUInt256);         // Zero fill happened correctly
		assertEquals(UInt256.ONE, bytesArrayAsUInt256); // Correct size array OK
	}

	@Test
	public void when_converting_int256_to_byte_array__the_correct_values_are_returned() {
		Int128 bp0 = Int128.from(0x0001_0203_0405_0607L, 0x0809_0A0B_0C0D_0E0FL);
		Int128 bp1 = Int128.from(0x1011_1213_1415_1617L, 0x1819_1A1B_1C1D_1E1FL);
		UInt256 bitPattern = UInt256.from(bp0, bp1);
		byte[] bytes2 = new byte[UInt256.BYTES * 3];
		Arrays.fill(bytes2, (byte) -1);

		// Make sure we got the value in big-endian order
		byte[] bytes = bitPattern.toByteArray();
		for (int i = 0; i < UInt256.BYTES; ++i) {
			assertEquals(i, bytes[i]);
		}

		bitPattern.toByteArray(bytes2, UInt256.BYTES);
		// Make sure we didn't overwrite bytes outside our range
		for (int i = 0; i < UInt256.BYTES; ++i) {
			assertEquals(-1, bytes2[i]);
			assertEquals(-1, bytes2[i + UInt256.BYTES * 2]);
		}
		// Make sure we got the value in big-endian order
		for (int i = 0; i < UInt256.BYTES; ++i) {
			assertEquals(i, bytes2[UInt256.BYTES + i]);
		}
	}

	@Test
	public void when_performing_binary_shifts__the_correct_value_is_returned() {
		final Int128 minusTwo = Int128.from(-2);
		// Basic cases, left shift
		assertEquals(UInt256.ZERO, UInt256.ZERO.shiftLeft());
		// Zero extend on left
		assertEquals(UInt256.from(Int128.MINUS_ONE, minusTwo), UInt256.MAX_VALUE.shiftLeft());
		assertEquals(UInt256.from(2), UInt256.ONE.shiftLeft());
		// Make sure bit crosses word boundary correctly
		assertEquals(UInt256.from(Int128.ONE, Int128.ZERO), UInt256.from(Int128.ZERO, Int128.MIN_VALUE).shiftLeft());

		// Basic cases, right shift
		assertEquals(UInt256.ZERO, UInt256.ZERO.shiftRight());
		// Zeros inserted at right
		assertEquals(UInt256.from(Int128.MAX_VALUE, Int128.MINUS_ONE), UInt256.MAX_VALUE.shiftRight());
		assertEquals(UInt256.ZERO, UInt256.ONE.shiftRight());
		assertEquals(UInt256.ONE, UInt256.from(2).shiftRight());
		// Make sure bit crosses word boundary correctly
		assertEquals(UInt256.from(Int128.ZERO, Int128.MIN_VALUE), UInt256.from(Int128.ONE, Int128.ZERO).shiftRight());
	}

	@Test
	public void when_performing_bitwise_inversion__the_correct_value_is_returned() {
		assertEquals(UInt256.MAX_VALUE, UInt256.ZERO.invert());
	}

	@Test
	public void when_using_predicates__the_correct_value_is_returned() {
		// Basic tests for odd/even
		assertTrue(UInt256.ONE.isOdd());
		assertFalse(UInt256.ONE.isEven());
		assertTrue(UInt256.MAX_VALUE.isOdd());
		assertFalse(UInt256.MAX_VALUE.isEven());
		UInt256 two = UInt256.ONE.add(UInt256.ONE);
		assertFalse(two.isOdd());
		assertTrue(two.isEven());
		UInt256 minusTwo = UInt256.MAX_VALUE.add(UInt256.MAX_VALUE);
		assertFalse(minusTwo.isOdd());
		assertTrue(minusTwo.isEven());

		assertFalse(UInt256.ONE.isZero());
		assertFalse(UInt256.MAX_VALUE.isZero());
		assertTrue(UInt256.ZERO.isZero());
	}

	@Test
	public void when_converting_int256_to_string__the_correct_value_is_returned() {
		// Some basics
		assertEquals("0", UInt256.ZERO.toString());
		assertEquals("1", UInt256.ONE.toString());
		assertEquals("10", UInt256.TEN.toString());

		assertEquals("12345678", UInt256.from(12345678L).toString());
		UInt256 maxPositive = UInt256.from(Int128.MINUS_ONE, Int128.MINUS_ONE);
		// Need to zero extend here to make positive
		byte[] bytes = new byte[UInt256.BYTES + 1];
		bytes[0] = 0;
		maxPositive.toByteArray(bytes, 1);
		assertEquals(new BigInteger(bytes).toString(), maxPositive.toString());
	}

	@Test
	public void when_converting_string_to_int256__the_correct_value_is_returned() {
		testRoundTrip("0");
		testRoundTrip("123456789");
		testRoundTrip("123456789123456789");
		testRoundTrip("123456789123456789123456789123456789");
		assertEquals(
			UInt256.from(Int128.MINUS_ONE, Int128.MINUS_ONE),
			UInt256.from(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE).toString())
		);
	}

	@Test
	public void when_calculating_powers__the_correct_value_is_returned() {
		assertEquals(UInt256.from(1L << 9), UInt256.TWO.pow(9));
		assertEquals(UInt256.from(10_000_000_000L), UInt256.TEN.pow(10));
		assertEquals(UInt256.ONE, UInt256.ZERO.pow(0)); // At least in the limit
		assertEquals(UInt256.ONE, UInt256.ONE.pow(0));
	}

	private static void testRoundTrip(String s) {
		assertEquals(s, UInt256.from(s).toString());
	}

	private static void assertEqualToLong(long expectedValue, UInt256 testValue) {
		assertEquals(0, testValue.getHigh().getHigh());
		assertEquals(0, testValue.getHigh().getLow());
		assertEquals(0, testValue.getLow().getHigh());
		assertEquals(expectedValue, testValue.getLow().getLow());
	}
}
