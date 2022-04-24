/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.math.BigIntegerMath;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

/** Basic unit tests for {@link UInt384}. */
public class UInt384Test {

  // Range of numbers to be tested at extremes for Integer and Long.
  private static final int TEST_RANGE = 1_000_000;

  /**
   * Exhaustively test construction for all non-negative {@code short} values, from {@code 0} up to
   * and including {@link Short.MAX_VALUE}.
   */
  @Test
  public void when_constructing_int384_from_short_values__values_compare_equal() {
    for (int i = 0; i <= Short.MAX_VALUE; ++i) {
      short s = (short) i;
      UInt384 int384 = UInt384.from(s);
      assertEqualToLong(s, int384);
    }
  }

  /**
   * Test construction for two ranges of {@code int} values from {@code 0} through to {@code
   * TEST_RANGE}, and also from {@code Integer.MAX_VALUE} down to {@code Integer.MAX_VALUE -
   * TEST_RANGE}.
   */
  @Test
  public void when_constructing_int384_from_int_values__values_compare_equal() {
    // Here we will just assume that testing some values near the
    // extremes of the range will suffice.
    for (int i = 0; i <= TEST_RANGE; ++i) {
      UInt384 int384 = UInt384.from(i);
      assertEqualToLong(i, int384);
    }
    for (int i = 0; i <= TEST_RANGE; ++i) {
      int ii = Integer.MAX_VALUE - i;
      UInt384 int384 = UInt384.from(ii);
      assertEqualToLong(ii, int384);
    }
  }

  /**
   * Test construction for two ranges of {@code long} values from {@code 0} through to {@code
   * TEST_RANGE}, and also from {@code Long.MAX_VALUE} down to {@code Long.MAX_VALUE - TEST_RANGE}.
   */
  @Test
  public void when_constructing_int384_from_long_values__accessors_compare_equal() {
    // Here we will just assume that testing some values near the
    // extremes of the range will suffice.
    for (int i = 0; i < TEST_RANGE; ++i) {
      long l = i;
      UInt384 int384 = UInt384.from(l);
      assertEqualToLong(l, int384);
    }
    for (int i = 0; i < TEST_RANGE; ++i) {
      long l = Long.MAX_VALUE - i;
      UInt384 int384 = UInt384.from(l);
      assertEqualToLong(l, int384);
    }
  }

  @Test
  public void when_performing_basic_addition__the_correct_values_are_returned() {
    // Some basics
    // 0 + 1 = 1
    assertEqualToLong(1L, UInt384.ZERO.add(UInt384.ONE));
    // 1 + 1 = 2
    assertEqualToLong(2L, UInt384.ONE.add(UInt384.ONE));
    // max + 1 = 0 (overflow)
    assertEqualToLong(0L, UInt384.MAX_VALUE.add(UInt384.ONE));
    // 1 + max = 0 (overflow)
    assertEqualToLong(0L, UInt384.ONE.add(UInt384.MAX_VALUE));

    // The half-add methods too
    // 0 + 1 = 1
    assertEqualToLong(1L, UInt384.ZERO.add(UInt256.ONE));
    // 1 + 1 = 2
    assertEqualToLong(2L, UInt384.ONE.add(UInt256.ONE));
    // max + 1 = 0 (overflow)
    assertEqualToLong(0L, UInt384.MAX_VALUE.add(UInt256.ONE));
  }

  @Test
  public void
      when_performing_addition_overflowing_between_words__the_correct_values_are_returned() {
    // Test adding with carry.
    UInt384 carry1 = UInt384.from(UInt128.ZERO, UInt256.MAX_VALUE).add(UInt384.ONE);
    assertEquals(UInt128.ONE, carry1.getHigh());
    assertEquals(UInt256.ZERO, carry1.getLow());

    // And also for the half-add method (actually 2/3 add in this case)
    // Test adding with carry.
    UInt384 carry2 = UInt384.from(UInt128.ZERO, UInt256.MAX_VALUE).add(UInt256.ONE);
    assertEquals(UInt128.ONE, carry2.getHigh());
    assertEquals(UInt256.ZERO, carry2.getLow());
  }

  @Test
  public void when_performing_basic_subtraction__the_correct_values_are_returned() {
    // Some basics
    // 1 - 1 = 0
    assertEqualToLong(0L, UInt384.ONE.subtract(UInt384.ONE));
    // 2 - 1 = 1
    assertEqualToLong(1L, UInt384.TWO.subtract(UInt384.ONE));
    // 0 - 1 = max (underflow)
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.subtract(UInt384.ONE));
    // 0 - max = 1 (underflow)
    assertEqualToLong(1L, UInt384.ZERO.subtract(UInt384.MAX_VALUE));

    // Half subtract methods also
    // 1 - 1 = 0
    assertEqualToLong(0L, UInt384.ONE.subtract(UInt256.ONE));
    // 2 - 1 = 1
    assertEqualToLong(1L, UInt384.TWO.subtract(UInt256.ONE));
    // 0 - 1 = max (underflow)
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.subtract(UInt256.ONE));
  }

  @Test
  public void
      when_performing_subtraction_underflowing_between_words__the_correct_value_is_returned() {
    // Test subtraction with carry.
    UInt384 carry1 = UInt384.from(UInt128.ONE, UInt256.ZERO).subtract(UInt384.ONE);
    assertEquals(UInt128.ZERO, carry1.high);
    assertEquals(UInt256.MAX_VALUE, carry1.low);
    UInt384 carry2 = UInt384.ZERO.subtract(UInt384.ONE); // underflow
    assertEquals(UInt128.MAX_VALUE, carry2.high);
    assertEquals(UInt256.MAX_VALUE, carry2.low);

    // also with half-subtract methods
    UInt384 carry3 = UInt384.from(UInt128.ONE, UInt256.ZERO).subtract(UInt256.ONE);
    assertEquals(UInt128.ZERO, carry3.high);
    assertEquals(UInt256.MAX_VALUE, carry3.low);
    UInt384 carry4 = UInt384.ZERO.subtract(UInt256.ONE); // underflow
    assertEquals(UInt128.MAX_VALUE, carry4.high);
    assertEquals(UInt256.MAX_VALUE, carry4.low);
  }

  @Test
  public void when_incrementing_int384__the_correct_values_are_returned() {
    assertEquals(UInt384.ONE, UInt384.ZERO.increment());
    assertEquals(UInt384.ZERO, UInt384.MAX_VALUE.increment()); // Internal and full overflow
  }

  @Test
  public void when_decrementing_int384__the_correct_values_are_returned() {
    assertEquals(UInt384.ZERO, UInt384.ONE.decrement());
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.decrement()); // Internal and full overflow
  }

  @Test
  public void when_multiplying_two_values__the_correct_value_is_returned() {
    // Some basics
    assertEquals(UInt384.ZERO, UInt384.ZERO.multiply(UInt384.ZERO));
    assertEquals(UInt384.ZERO, UInt384.ZERO.multiply(UInt384.ONE));
    assertEquals(UInt384.ZERO, UInt384.ONE.multiply(UInt384.ZERO));
    assertEquals(UInt384.ONE, UInt384.ONE.multiply(UInt384.ONE));

    // Some values in the long range
    assertEquals(
        UInt384.from(12345678L * 13L), UInt384.from(12345678L).multiply(UInt384.from(13L)));
  }

  @Test
  public void when_multiplying_uint256_values__the_correct_value_is_returned() {
    // Some basics
    assertEquals(UInt384.ZERO, UInt384.ZERO.multiply(UInt256.ZERO));
    assertEquals(UInt384.ZERO, UInt384.ZERO.multiply(UInt256.ONE));
    assertEquals(UInt384.ZERO, UInt384.ONE.multiply(UInt256.ZERO));
    assertEquals(UInt384.ONE, UInt384.ONE.multiply(UInt256.ONE));

    // Some values in the long range
    assertEquals(
        UInt384.from(12345678L * 13L), UInt384.from(12345678L).multiply(UInt256.from(13L)));
  }

  @Test
  public void when_dividing_one_value_by_another__the_correct_value_is_returned() {
    // Some basics
    assertEquals(UInt384.ZERO, UInt384.ZERO.divide(UInt384.ONE));
    assertEquals(UInt384.ONE, UInt384.ONE.divide(UInt384.ONE));

    // Some values in the long range
    assertEquals(UInt384.from(12345678L / 13L), UInt384.from(12345678L).divide(UInt384.from(13L)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void when_dividing_by_zero__an_exception_is_thrown() {
    UInt384.ONE.divide(UInt384.ZERO);
    fail();
  }

  @Test
  public void when_dividing_one_value_by_another_int256__the_correct_value_is_returned() {
    // Some basics
    assertEquals(UInt384.ZERO, UInt384.ZERO.divide(UInt256.ONE));
    assertEquals(UInt384.ONE, UInt384.ONE.divide(UInt256.ONE));
    assertEquals(UInt384.MAX_VALUE, UInt384.MAX_VALUE.divide(UInt256.ONE));
    assertEquals(
        UInt384.ONE.shiftLeft(UInt384.SIZE - UInt256.SIZE),
        UInt384.MAX_VALUE.divide(UInt256.MAX_VALUE));

    // Some values in the long range
    assertEquals(UInt384.from(12345678L / 13L), UInt384.from(12345678L).divide(UInt256.from(13L)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void when_dividing_by_zero_int256__an_exception_is_thrown() {
    UInt384.ONE.divide(UInt256.ZERO);
    fail();
  }

  @Test
  public void
      when_computing_the_remainder_of_dividing_one_value_by_another__the_correct_value_is_returned() {
    // Some basics
    assertEquals(UInt384.ZERO, UInt384.ZERO.remainder(UInt384.ONE));
    assertEquals(UInt384.ZERO, UInt384.ONE.remainder(UInt384.ONE));
    assertEquals(UInt384.ONE, UInt384.ONE.remainder(UInt384.TWO));

    // Some values in the long range
    assertEquals(
        UInt384.from(12345678L % 13L), UInt384.from(12345678L).remainder(UInt384.from(13L)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void when_computing_the_remainder_of_dividing_by_zero__an_exception_is_thrown() {
    UInt384.ONE.remainder(UInt384.ZERO);
    fail();
  }

  @Test
  public void when_comparing_int384_values_using_compareTo__the_correct_value_is_returned() {
    assertThat(UInt384.ZERO)
        .isEqualByComparingTo(UInt384.ZERO)
        .isLessThan(UInt384.ONE)
        .isLessThan(UInt384.MAX_VALUE);
    assertThat(UInt384.ONE).isGreaterThan(UInt384.ZERO);
    assertThat(UInt384.MAX_VALUE).isGreaterThan(UInt384.ZERO);

    UInt384 i127 = UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT);
    UInt384 i128 = i127.add(i127);
    UInt384 i129 = i128.add(i128);
    assertThat(i128).isGreaterThan(i127); // In case something has gone horribly wrong.
    assertThat(i128.add(i127)).isGreaterThan(i128);
    assertThat(i129).isGreaterThan(i128.add(i127));
  }

  @Test
  public void when_comparing_int384_values_using_equals__the_correct_value_is_returned() {
    assertNotEquals(UInt384.ZERO, null); // Nothing should be equal to null
    assertEquals(UInt384.ZERO, UInt384.ZERO); // Same object check
    UInt384 i127a = UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT);
    UInt384 i127b = UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT);
    assertEquals(i127a, i127b);
    assertNotEquals(i127a, i127b.add(i127b));
    assertNotEquals(i127a, UInt384.ZERO);
  }

  @Test
  public void when_calculating_leading_zeros__the_correct_value_is_returned() {
    assertEquals(UInt384.SIZE, UInt384.ZERO.numberOfLeadingZeros());
    assertEquals(UInt384.SIZE - 1, UInt384.ONE.numberOfLeadingZeros());
    assertEquals(0, UInt384.MAX_VALUE.numberOfLeadingZeros());
    assertEquals(UInt128.SIZE - 1, UInt384.from(UInt128.ONE, UInt256.ZERO).numberOfLeadingZeros());
    assertEquals(UInt128.SIZE, UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT).numberOfLeadingZeros());
  }

  @Test
  public void when_binary_oring_two_values__the_correct_value_is_returned() {
    // Use bit positions in both high and low word for tests
    UInt384 b0 = UInt384.ONE;
    UInt384 b128 = UInt384.from(UInt128.ONE, UInt256.ZERO);
    // Basic sanity checks to make sure nothing is horribly wrong
    assertThat(b128).isGreaterThan(b0);
    assertThat(b0).isGreaterThan(UInt384.ZERO);
    assertThat(b128).isGreaterThan(UInt384.ZERO);

    // Now for the real tests
    assertEquals(b0, UInt384.ZERO.or(b0));
    assertEquals(b0, b0.or(b0));
    assertEquals(b128, UInt384.ZERO.or(b128));
    assertEquals(b128, b128.or(b128));
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.or(UInt384.MAX_VALUE));
    assertEquals(UInt384.MAX_VALUE, UInt384.MAX_VALUE.or(UInt384.MAX_VALUE));
  }

  @Test
  public void when_binary_anding_two_values__the_correct_value_is_returned() {
    // Use bit positions in both high and low word for tests
    UInt384 b0 = UInt384.ONE;
    UInt384 b128 = UInt384.from(UInt128.ONE, UInt256.ZERO);
    assertEquals(b0, UInt384.MAX_VALUE.and(b0));
    assertEquals(b128, UInt384.MAX_VALUE.and(b128));
    assertEquals(UInt384.ZERO, UInt384.MAX_VALUE.and(UInt384.ZERO));
  }

  @Test
  public void when_binary_xoring_two_values__the_correct_value_is_returned() {
    // Use bit positions in both high and low word for tests
    UInt384 b0 = UInt384.ONE;
    UInt384 b128 = UInt384.from(UInt128.ONE, UInt256.ZERO);
    assertEquals(b0, UInt384.ZERO.xor(b0));
    assertEquals(UInt384.ZERO, b0.xor(b0));
    assertEquals(b128, UInt384.ZERO.xor(b128));
    assertEquals(UInt384.ZERO, b128.xor(b128));
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.xor(UInt384.MAX_VALUE));
    assertEquals(UInt384.ZERO, UInt384.MAX_VALUE.xor(UInt384.MAX_VALUE));
  }

  @Test
  public void when_creating_int256_from_byte_array__the_correct_value_is_created() {
    byte[] m1 = {-1};
    byte[] p1 = {1};
    byte[] bytesArray = new byte[UInt384.BYTES];
    Arrays.fill(bytesArray, (byte) 0);
    bytesArray[UInt384.BYTES - 1] = 1;
    UInt384 m1Bits128 = UInt384.from(m1);
    UInt384 p1Bits128 = UInt384.from(p1);
    UInt384 bytesArrayBits128 = UInt384.from(bytesArray);

    assertEquals(UInt384.from(255), m1Bits128); // Sign extension did not happen
    assertEquals(UInt384.ONE, p1Bits128); // Zero fill happened correctly
    assertEquals(UInt384.ONE, bytesArrayBits128); // Correct size array OK
  }

  @Test
  public void when_converting_int256_to_byte_array__the_correct_values_are_returned() {
    UInt128 bp0 = UInt128.from(0x0001_0203_0405_0607L, 0x0809_0A0B_0C0D_0E0FL);
    UInt256 bp1 =
        UInt256.from(
            UInt128.from(0x1011_1213_1415_1617L, 0x1819_1A1B_1C1D_1E1FL),
            UInt128.from(0x2021_2223_2425_2627L, 0x2829_2A2B_2C2D_2E2FL));
    UInt384 bitPattern = UInt384.from(bp0, bp1);
    byte[] bytes2 = new byte[UInt384.BYTES * 3];
    Arrays.fill(bytes2, (byte) -1);

    // Make sure we got the value in big-endian order
    byte[] bytes = bitPattern.toByteArray();
    for (int i = 0; i < UInt384.BYTES; ++i) {
      assertEquals(i, bytes[i]);
    }

    bitPattern.toByteArray(bytes2, UInt384.BYTES);
    // Make sure we didn't overwrite bytes outside our range
    for (int i = 0; i < UInt384.BYTES; ++i) {
      assertEquals(-1, bytes2[i]);
      assertEquals(-1, bytes2[i + UInt384.BYTES * 2]);
    }
    // Make sure we got the value in big-endian order
    for (int i = 0; i < UInt384.BYTES; ++i) {
      assertEquals(i, bytes2[UInt384.BYTES + i]);
    }
  }

  @Test
  public void when_performing_binary_shifts__the_correct_value_is_returned() {
    final UInt256 minusTwo = UInt256.ZERO.decrement().decrement();
    final UInt128 maxSigned = UInt128.HIGH_BIT.decrement();

    // Basic cases, left shift
    assertEquals(UInt384.ZERO, UInt384.ZERO.shiftLeft());
    // Zero extend on left
    assertEquals(UInt384.from(UInt128.MAX_VALUE, minusTwo), UInt384.MAX_VALUE.shiftLeft());
    assertEquals(UInt384.from(2), UInt384.ONE.shiftLeft());
    // Make sure bit crosses word boundary correctly
    assertEquals(
        UInt384.from(UInt128.ONE, UInt256.ZERO),
        UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT).shiftLeft());

    // Basic cases, right shift
    assertEquals(UInt384.ZERO, UInt384.ZERO.shiftRight());
    // Zeros inserted at right
    assertEquals(UInt384.from(maxSigned, UInt256.MAX_VALUE), UInt384.MAX_VALUE.shiftRight());
    assertEquals(UInt384.ZERO, UInt384.ONE.shiftRight());
    assertEquals(UInt384.ONE, UInt384.from(2).shiftRight());
    // Make sure bit crosses word boundary correctly
    assertEquals(
        UInt384.from(UInt128.ZERO, UInt256.HIGH_BIT),
        UInt384.from(UInt128.ONE, UInt256.ZERO).shiftRight());
  }

  /** Test multi shiftLeft. */
  @Test
  public void when_performing_binary_left_shifts_by_n__the_correct_value_is_returned() {
    for (int i = 0; i < UInt384.SIZE; ++i) {
      UInt384 powNum = UInt384.TWO.pow(i);
      UInt384 shiftNum = UInt384.ONE.shiftLeft(i);
      assertEquals(powNum, shiftNum);
    }
    assertEquals(UInt384.ZERO, UInt384.ONE.shiftLeft(UInt384.SIZE));
    assertEquals(UInt384.ZERO, UInt384.ONE.shiftLeft(-UInt384.SIZE));
    assertEquals(UInt384.ONE, UInt384.TWO.shiftLeft(-1));
  }

  /** Test multi shiftRight. */
  @Test
  public void when_performing_binary_right_shifts_by_n__the_correct_value_is_returned() {
    for (int i = 0; i < UInt384.SIZE; ++i) {
      UInt384 powNum = UInt384.TWO.pow(UInt384.SIZE - (i + 1));
      UInt384 shiftNum = UInt384.HIGH_BIT.shiftRight(i);
      assertEquals(powNum, shiftNum);
    }
    assertEquals(UInt384.ZERO, UInt384.HIGH_BIT.shiftRight(UInt384.SIZE));
    assertEquals(UInt384.ZERO, UInt384.HIGH_BIT.shiftRight(-UInt384.SIZE));
    assertEquals(UInt384.TWO, UInt384.ONE.shiftRight(-1));
  }

  /** Integer square root. */
  @Test
  public void when_performing_integer_square_root__the_correct_value_is_returned() {
    long max = 1 << 53; // Precision of double
    for (long n = 0; n < max; n += 997) {
      long lsqrt = (long) Math.floor(Math.sqrt(n));
      UInt384 nn = UInt384.from(n);
      UInt384 isqrt = nn.isqrt();
      assertEquals(UInt384.from(lsqrt), isqrt);
    }
    for (int n = 0; n < UInt256.SIZE; ++n) {
      UInt384 num = UInt384.ONE.shiftLeft(n);
      byte[] bytes = num.toByteArray();
      BigInteger nsqrt = BigIntegerMath.sqrt(new BigInteger(1, bytes), RoundingMode.FLOOR);
      byte[] otherBytes = num.isqrt().toByteArray();
      BigInteger isqrt = new BigInteger(1, otherBytes);
      assertEquals(nsqrt, isqrt);
    }
  }

  @Test
  public void when_performing_bitwise_inversion__the_correct_value_is_returned() {
    assertEquals(UInt384.MAX_VALUE, UInt384.ZERO.invert());
  }

  @Test
  public void when_using_predicates__the_correct_value_is_returned() {
    // Basic tests for odd/even
    assertTrue(UInt384.ONE.isOdd());
    assertFalse(UInt384.ONE.isEven());
    assertTrue(UInt384.MAX_VALUE.isOdd());
    assertFalse(UInt384.MAX_VALUE.isEven());
    UInt384 two = UInt384.ONE.add(UInt384.ONE);
    assertFalse(two.isOdd());
    assertTrue(two.isEven());
    UInt384 minusTwo = UInt384.MAX_VALUE.add(UInt384.MAX_VALUE);
    assertFalse(minusTwo.isOdd());
    assertTrue(minusTwo.isEven());

    assertFalse(UInt384.ONE.isZero());
    assertFalse(UInt384.MAX_VALUE.isZero());
    assertTrue(UInt384.ZERO.isZero());
  }

  @Test
  public void when_converting_int384_to_string__the_correct_value_is_returned() {
    // Some basics
    assertEquals("0", UInt384.ZERO.toString());
    assertEquals("1", UInt384.ONE.toString());
    assertEquals("10", UInt384.TEN.toString());

    assertEquals("12345678", UInt384.from(12345678L).toString());
    UInt384 maxPositive = UInt384.from(UInt128.MAX_VALUE, UInt256.MAX_VALUE);
    // Need to zero extend here to make positive
    byte[] bytes = new byte[UInt384.BYTES + 1];
    bytes[0] = 0;
    maxPositive.toByteArray(bytes, 1);
    assertEquals(new BigInteger(bytes).toString(), maxPositive.toString());

    int lastBit = UInt384.SIZE - 1;
    assertFalse(UInt384.ZERO.isHighBitSet());
    assertFalse(UInt384.TWO.pow(lastBit - 1).isHighBitSet());
    assertFalse(UInt384.TWO.pow(lastBit).decrement().isHighBitSet());
    assertTrue(UInt384.MAX_VALUE.isHighBitSet());
    assertTrue(UInt384.ONE.invert().isHighBitSet());
    assertTrue(UInt384.TWO.pow(lastBit).isHighBitSet());
    assertTrue(UInt384.HIGH_BIT.isHighBitSet());
  }

  @Test
  public void when_converting_string_to_int384__the_correct_value_is_returned() {
    testRoundTrip("0");
    testRoundTrip("123456789");
    testRoundTrip("123456789123456789");
    testRoundTrip("123456789123456789123456789123456789");
    assertEquals(
        UInt384.from(UInt128.MAX_VALUE, UInt256.MAX_VALUE),
        UInt384.from(BigInteger.ONE.shiftLeft(384).subtract(BigInteger.ONE).toString()));
  }

  @Test
  public void when_calculating_powers__the_correct_value_is_returned() {
    assertEquals(UInt384.from(1L << 9), UInt384.TWO.pow(9));
    assertEquals(UInt384.from(10_000_000_000L), UInt384.TEN.pow(10));
    assertEquals(UInt384.ONE, UInt384.ZERO.pow(0)); // At least in the limit
    assertEquals(UInt384.ONE, UInt384.ONE.pow(0));
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(UInt384.class).verify();
  }

  /** Test div 0. */
  @Test(expected = IllegalArgumentException.class)
  public void testDiv0() {
    UInt384.ONE.divide(UInt384.ZERO);
  }

  /** Test rem 0. */
  @Test(expected = IllegalArgumentException.class)
  public void testRem0() {
    UInt384.ONE.remainder(UInt384.ZERO);
  }

  /** NumberFormatException on empty string. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionOnEmpty() {
    UInt384.from("");
  }

  /** NumberFormatException if no actual number. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionIfNoNumber() {
    UInt384.from("+");
  }

  /** NumberFormatException if invalid digit. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionIfInvalidDigit() {
    UInt384.from("+a");
  }

  /** IllegalArgumentException if byte array is empty. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionIfByteArrayEmpty() {
    UInt384.from(new byte[0]);
  }

  /** IllegalArgumentException on radix too big. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnRadixTooBig() {
    UInt384.ONE.toString(Character.MAX_RADIX + 1);
  }

  /** IllegalArgumentException on radix too small. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnRadixTooSmall() {
    UInt384.ONE.toString(Character.MIN_RADIX - 1);
  }

  /** IllegalArgumentException on negative exponent for pow. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnNegativeExponent() {
    UInt384.ONE.pow(-1);
  }

  private static void testRoundTrip(String s) {
    assertEquals(s, UInt384.from(s).toString());
  }

  private static void assertEqualToLong(long expectedValue, UInt384 testValue) {
    assertEquals(UInt128.ZERO, testValue.getHigh());
    assertEquals(UInt128.ZERO, testValue.getLow().getHigh());
    assertEquals(0L, testValue.getLow().getHigh().getHigh());
    assertEquals(expectedValue, testValue.low.low.getLow());
  }
}
