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

import com.google.common.math.BigIntegerMath;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

/** Basic unit tests for {@link UInt128}. */
public class UInt128Test {

  // Range of numbers to be tested at extremes for Integer and Long.
  private static final int TEST_RANGE = 1_000_000;

  /**
   * Exhaustively test construction and accessors from {@link Number} for all {@code short} values,
   * from {@link Short.MIN_VALUE} up to and including {@link Short.MAX_VALUE}.
   */
  @Test
  public void testShortValues() {
    for (int i = Short.MIN_VALUE; i <= Short.MAX_VALUE; ++i) {
      short s = (short) i;
      UInt128 int128 = UInt128.from(s);
      assertEquals(s & 0xFFFFL, int128.getLow());
      assertEquals(0, int128.getHigh());
    }
  }

  /**
   * Test construction and accessors from {@link Number} for two ranges of {@code int} values from
   * {@code Integer.MIN_VALUE} through to {@code Integer.MIN_VALUE + TEST_RANGE}, and also from
   * {@code Integer.MAX_VALUE} down to {@code Integer.MAX_VALUE - TEST_RANGE}.
   */
  @Test
  public void testIntValue() {
    // Here we will just assume that testing some values near the
    // extremes of the range will suffice.
    for (int i = 0; i <= TEST_RANGE; ++i) {
      int ii = Integer.MIN_VALUE + i;
      UInt128 int128 = UInt128.from(ii);
      assertEquals(ii & 0xFFFF_FFFFL, int128.getLow());
      assertEquals(0, int128.getHigh());
    }
    for (int i = 0; i <= TEST_RANGE; ++i) {
      int ii = Integer.MAX_VALUE - i;
      UInt128 int128 = UInt128.from(ii);
      assertEquals(ii & 0xFFFF_FFFFL, int128.getLow());
      assertEquals(0, int128.getHigh());
    }
  }

  /**
   * Test construction and accessors from {@link Number} for two ranges of {@code long} values from
   * {@code Long.MIN_VALUE} through to {@code Long.MIN_VALUE + TEST_RANGE}, and also from {@code
   * Long.MAX_VALUE} down to {@code Long.MAX_VALUE - TEST_RANGE}.
   */
  @Test
  public void testLongValue() {
    // Here we will just assume that testing some values near the
    // extremes of the range will suffice.
    for (int i = 0; i < TEST_RANGE; ++i) {
      long l = Long.MIN_VALUE + i;
      UInt128 int128 = UInt128.from(l);
      assertEquals(l, int128.getLow());
      assertEquals(0L, int128.getHigh());
    }
    for (int i = 0; i < TEST_RANGE; ++i) {
      long l = Long.MAX_VALUE - i;
      UInt128 int128 = UInt128.from(l);
      assertEquals(l, int128.getLow());
      assertEquals(0L, int128.getHigh());
    }
  }

  /**
   * Test to ensure that addition is functioning properly, including negative numbers, and overflow
   * between the two underlying long values.
   */
  @Test
  public void testAddition() {
    // Some basics
    // 0 + 1 = 1
    assertEquals(1L, UInt128.ZERO.add(UInt128.ONE).getLow());
    // 1 + 1 = 2
    assertEquals(2L, UInt128.ONE.add(UInt128.ONE).getLow());
    // 0 + MAX_VALUE = MAX_VALUE
    assertEquals(-1L, UInt128.ZERO.add(UInt128.MAX_VALUE).getLow());
    // MAX_VALUE + 1 = 0 (wrap)
    assertEquals(0L, UInt128.MAX_VALUE.add(UInt128.ONE).getLow());
    // MAX_VALUE + MAX_VALUE = MAX_VALUE - 1 (wrap)
    assertEquals(-2L, UInt128.MAX_VALUE.add(UInt128.MAX_VALUE).getLow());

    // Test adding with carry.
    UInt128 carry1 = UInt128.from(0x0000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFFFL).add(UInt128.ONE);
    assertEquals(0x0000_0000_0000_0001L, carry1.getHigh());
    assertEquals(0x0000_0000_0000_0000L, carry1.getLow());
    UInt128 carry2 = UInt128.ZERO.add(UInt128.MAX_VALUE);
    assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getHigh());
    assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getLow());
  }

  /**
   * Test to ensure that subtraction is functioning properly, including negative numbers, and
   * overflow between the two underlying long values.
   */
  @Test
  public void testSubtraction() {
    // Some basics
    // 0 - 1 = MAX_VALUE (wrap)
    assertEquals(-1L, UInt128.ZERO.subtract(UInt128.ONE).getLow());
    // 1 - 1 = 0
    assertEquals(0L, UInt128.ONE.subtract(UInt128.ONE).getLow());
    // 0 - MAX_VALUE = 1 (wrap)
    assertEquals(1L, UInt128.ZERO.subtract(UInt128.MAX_VALUE).getLow());
    // MAX_VALUE - 1 = MAX_VALUE-1
    assertEquals(-2L, UInt128.MAX_VALUE.subtract(UInt128.ONE).getLow());
    // MAX_VALUE - MAX_VALUE = 0
    assertEquals(0L, UInt128.MAX_VALUE.subtract(UInt128.MAX_VALUE).getLow());

    // Test adding with carry.
    UInt128 carry1 =
        UInt128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000L).subtract(UInt128.ONE);
    assertEquals(0x0000_0000_0000_0000L, carry1.getHigh());
    assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry1.getLow());
    UInt128 carry2 = UInt128.ZERO.subtract(UInt128.ONE);
    assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getHigh());
    assertEquals(0xFFFF_FFFF_FFFF_FFFFL, carry2.getLow());
  }

  /** Test multiplication. */
  @Test
  public void testMultiplication() {
    // Some basics
    assertEquals(UInt128.ZERO, UInt128.ZERO.multiply(UInt128.ONE));
    assertEquals(UInt128.ZERO, UInt128.ONE.multiply(UInt128.ZERO));
    assertEquals(UInt128.MAX_VALUE, UInt128.ONE.multiply(UInt128.MAX_VALUE));
    assertEquals(UInt128.MAX_VALUE, UInt128.MAX_VALUE.multiply(UInt128.ONE));
    assertEquals(UInt128.ONE, UInt128.MAX_VALUE.multiply(UInt128.MAX_VALUE));

    // Some values in the long range
    assertEquals(
        UInt128.from(12345678L * 13L), UInt128.from(12345678L).multiply(UInt128.from(13L)));
  }

  /** Test division. */
  @Test
  public void testDivision() {
    // Some basics
    assertEquals(UInt128.ZERO, UInt128.ZERO.divide(UInt128.ONE));
    assertEquals(UInt128.ZERO, UInt128.ONE.divide(UInt128.MAX_VALUE));
    assertEquals(UInt128.MAX_VALUE, UInt128.MAX_VALUE.divide(UInt128.ONE));
    assertEquals(UInt128.ONE, UInt128.MAX_VALUE.divide(UInt128.MAX_VALUE));

    // Some values in the long range
    assertEquals(UInt128.from(12345678L / 13L), UInt128.from(12345678L).divide(UInt128.from(13L)));
  }

  /** Test remainder. */
  @Test
  public void testRemainder() {
    // Some basics
    UInt128 two = UInt128.from(2);
    assertEquals(UInt128.ZERO, UInt128.ZERO.remainder(UInt128.ONE));
    assertEquals(UInt128.ONE, UInt128.ONE.remainder(UInt128.MAX_VALUE));
    assertEquals(UInt128.ZERO, UInt128.MAX_VALUE.remainder(UInt128.ONE));
    assertEquals(UInt128.ONE, UInt128.MAX_VALUE.remainder(two));
    assertEquals(UInt128.ONE, UInt128.ONE.remainder(two));

    // Some values in the long range
    assertEquals(
        UInt128.from(12345678L % 13L), UInt128.from(12345678L).remainder(UInt128.from(13L)));
  }

  /** Basic tests for comparisons. */
  @Test
  public void testCompare() {
    assertThat(UInt128.ZERO)
        .isEqualByComparingTo(UInt128.ZERO)
        .isLessThan(UInt128.ONE)
        .isLessThan(UInt128.MAX_VALUE);
    assertThat(UInt128.MAX_VALUE).isGreaterThan(UInt128.ONE);
    assertThat(UInt128.ONE).isGreaterThan(UInt128.ZERO);

    UInt128 i63 = UInt128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
    UInt128 i64 = i63.add(i63);
    UInt128 i65 = i64.add(i64);
    assertThat(i64).isGreaterThan(i63); // In case something has gone horribly wrong.
    assertThat(i64.add(i63)).isGreaterThan(i64);
    assertThat(i65).isGreaterThan(i64.add(i63));
  }

  /** Basic tests for equals(...) method. */
  @Test
  public void testEquals() {
    assertNotEquals(UInt128.ZERO, null); // Nothing should be equal to null
    assertEquals(UInt128.ZERO, UInt128.ZERO); // Same object check
    UInt128 i63a = UInt128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
    UInt128 i63b = UInt128.from(0x0000_0000_0000_0000L, 0x8000_0000_0000_0000L);
    assertEquals(i63a, i63b);
    assertNotEquals(i63a, i63a.add(i63b));
    assertNotEquals(i63a, UInt128.ZERO);
  }

  /** Tests for numberOfLeadingZeros(). */
  @Test
  public void testNumberOfLeadingZeros() {
    assertEquals(UInt128.SIZE, UInt128.ZERO.numberOfLeadingZeros());
    assertEquals(UInt128.SIZE - 1, UInt128.ONE.numberOfLeadingZeros());
    assertEquals(0, UInt128.MAX_VALUE.numberOfLeadingZeros());
    assertEquals(
        63, UInt128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000).numberOfLeadingZeros());
  }

  /** Tests for bit operations. */
  @Test
  public void testBitOperations() {
    // Use bit positions in both high and low word for tests
    UInt128 b0 = UInt128.ONE;
    UInt128 b64 = UInt128.from(0x0000_0000_0000_0001L, 0x0000_0000_0000_0000);
    // Basic sanity checks to make sure nothing is horribly wrong
    assertThat(b64).isGreaterThan(b0);
    assertThat(b0).isGreaterThan(UInt128.ZERO);
    assertThat(b64).isGreaterThan(UInt128.ZERO);

    // Now for the real tests
    assertEquals(b0, UInt128.ZERO.or(b0));
    assertEquals(b0, b0.or(b0));
    assertEquals(b64, UInt128.ZERO.or(b64));
    assertEquals(b64, b64.or(b64));
    assertEquals(UInt128.MAX_VALUE, UInt128.ZERO.or(UInt128.MAX_VALUE));
    assertEquals(UInt128.MAX_VALUE, UInt128.MAX_VALUE.or(UInt128.MAX_VALUE));

    assertEquals(b0, UInt128.MAX_VALUE.and(b0));
    assertEquals(b64, UInt128.MAX_VALUE.and(b64));
    assertEquals(UInt128.ZERO, UInt128.MAX_VALUE.and(UInt128.ZERO));

    assertEquals(b0, UInt128.ZERO.xor(b0));
    assertEquals(UInt128.ZERO, b0.xor(b0));
    assertEquals(b64, UInt128.ZERO.xor(b64));
    assertEquals(UInt128.ZERO, b64.xor(b64));
    assertEquals(UInt128.MAX_VALUE, UInt128.ZERO.xor(UInt128.MAX_VALUE));
    assertEquals(UInt128.ZERO, UInt128.MAX_VALUE.xor(UInt128.MAX_VALUE));
  }

  /** Test creation from byte array. */
  @Test
  public void testCreateFromByteArray() {
    byte[] m1 = {-1};
    byte[] p1 = {1};
    byte[] bytesArray = new byte[UInt128.BYTES];
    Arrays.fill(bytesArray, (byte) 0);
    bytesArray[UInt128.BYTES - 1] = 1;
    UInt128 m1Bits128 = UInt128.from(m1);
    UInt128 p1Bits128 = UInt128.from(p1);
    UInt128 bytesArrayBits128 = UInt128.from(bytesArray);

    assertEquals(UInt128.from(255), m1Bits128); // Zero extension happened correctly
    assertEquals(UInt128.ONE, p1Bits128); // Zero fill happened correctly
    assertEquals(UInt128.ONE, bytesArrayBits128); // Correct size array OK
  }

  /** Test toByteArray(...) methods. */
  @Test
  public void testToByteArray() {
    UInt128 bitPattern = UInt128.from(0x0001_0203_0405_0607L, 0x0809_0A0B_0C0D_0E0FL);
    byte[] bytes2 = new byte[UInt128.BYTES * 3];
    Arrays.fill(bytes2, (byte) -1);

    // Make sure we got the value in big-endian order
    byte[] bytes = bitPattern.toByteArray();
    for (int i = 0; i < UInt128.BYTES; ++i) {
      assertEquals(i, bytes[i]);
    }

    bitPattern.toByteArray(bytes2, UInt128.BYTES);
    // Make sure we didn't overwrite bytes outside our range
    for (int i = 0; i < UInt128.BYTES; ++i) {
      assertEquals(-1, bytes2[i]);
      assertEquals(-1, bytes2[i + UInt128.BYTES * 2]);
    }
    // Make sure we got the value in big-endian order
    for (int i = 0; i < UInt128.BYTES; ++i) {
      assertEquals(i, bytes2[UInt128.BYTES + i]);
    }
  }

  /** Test shiftLeft and shiftRight. */
  @Test
  public void testShifts() {
    // Basic cases, left shift
    assertEquals(UInt128.ZERO, UInt128.ZERO.shiftLeft());
    // Zero extend on left
    assertEquals(UInt128.from(-1L, -2L), UInt128.MAX_VALUE.shiftLeft());
    assertEquals(UInt128.from(0L, 2L), UInt128.ONE.shiftLeft());
    // Make sure bit crosses word boundary correctly
    assertEquals(UInt128.from(1L, 0L), UInt128.from(0L, 1L << (Long.SIZE - 1)).shiftLeft());

    // Basic cases, right shift
    assertEquals(UInt128.ZERO, UInt128.ZERO.shiftRight());
    // Sign extend on right
    assertEquals(UInt128.from(0x7FFF_FFFF_FFFF_FFFFL, -1), UInt128.MAX_VALUE.shiftRight());
    assertEquals(UInt128.ZERO, UInt128.ONE.shiftRight());
    assertEquals(UInt128.ONE, UInt128.from(0L, 2L).shiftRight());
    // Make sure bit crosses word boundary correctly
    assertEquals(UInt128.from(0L, 0x8000_0000_0000_0000L), UInt128.from(1L, 0L).shiftRight());
  }

  /** Test multi shiftLeft. */
  @Test
  public void testMultiShiftLeft() {
    for (int i = 0; i < UInt128.SIZE; ++i) {
      UInt128 powNum = UInt128.TWO.pow(i);
      UInt128 shiftNum = UInt128.ONE.shiftLeft(i);
      assertEquals(powNum, shiftNum);
    }
    assertEquals(UInt128.ZERO, UInt128.ONE.shiftLeft(UInt128.SIZE));
    assertEquals(UInt128.ZERO, UInt128.ONE.shiftLeft(-UInt128.SIZE));
    assertEquals(UInt128.ONE, UInt128.TWO.shiftLeft(-1));
  }

  /** Test multi shiftRight. */
  @Test
  public void testMultiShiftRight() {
    for (int i = 0; i < UInt128.SIZE; ++i) {
      UInt128 powNum = UInt128.TWO.pow(UInt128.SIZE - (i + 1));
      UInt128 shiftNum = UInt128.HIGH_BIT.shiftRight(i);
      assertEquals(powNum, shiftNum);
    }
    assertEquals(UInt128.ZERO, UInt128.HIGH_BIT.shiftRight(UInt128.SIZE));
    assertEquals(UInt128.ZERO, UInt128.HIGH_BIT.shiftRight(-UInt128.SIZE));
    assertEquals(UInt128.TWO, UInt128.ONE.shiftRight(-1));
  }

  /** Integer square root. */
  @Test
  public void testIntegerSquareRoot() {
    long max = 1 << 53; // Precision of double
    for (long n = 0; n < max; n += 997) {
      long lsqrt = (long) Math.floor(Math.sqrt(n));
      UInt128 nn = UInt128.from(n);
      UInt128 isqrt = nn.isqrt();
      assertEquals(UInt128.from(lsqrt), isqrt);
    }
    for (int n = 0; n < UInt128.SIZE; ++n) {
      UInt128 num = UInt128.ONE.shiftLeft(n);
      byte[] bytes = num.toByteArray();
      BigInteger nsqrt = BigIntegerMath.sqrt(new BigInteger(1, bytes), RoundingMode.FLOOR);
      byte[] otherBytes = num.isqrt().toByteArray();
      BigInteger isqrt = new BigInteger(1, otherBytes);
      assertEquals(nsqrt, isqrt);
    }
  }

  /** Test isEven(), isOdd(), isZero(), isHighBitSet() predicates. */
  @Test
  public void testPredicates() {
    // Basic tests for odd/even
    assertTrue(UInt128.ONE.isOdd());
    assertFalse(UInt128.ONE.isEven());
    assertTrue(UInt128.MAX_VALUE.isOdd());
    assertFalse(UInt128.MAX_VALUE.isEven());
    UInt128 two = UInt128.ONE.add(UInt128.ONE);
    assertFalse(two.isOdd());
    assertTrue(two.isEven());
    UInt128 minusTwo = UInt128.MAX_VALUE.add(UInt128.MAX_VALUE);
    assertFalse(minusTwo.isOdd());
    assertTrue(minusTwo.isEven());

    assertFalse(UInt128.ONE.isZero());
    assertFalse(UInt128.MAX_VALUE.isZero());
    assertTrue(UInt128.ZERO.isZero());

    assertFalse(UInt128.ZERO.isHighBitSet());
    assertFalse(UInt128.TWO.pow(126).isHighBitSet());
    assertFalse(UInt128.TWO.pow(127).decrement().isHighBitSet());
    assertTrue(UInt128.MAX_VALUE.isHighBitSet());
    assertTrue(UInt128.ONE.invert().isHighBitSet());
    assertTrue(UInt128.TWO.pow(127).isHighBitSet());
    assertTrue(UInt128.HIGH_BIT.isHighBitSet());
  }

  /** Test toString() method. */
  @Test
  public void testToString() {
    // Some basics
    assertEquals("0", UInt128.ZERO.toString());
    assertEquals("1", UInt128.ONE.toString());
    assertEquals("340282366920938463463374607431768211455", UInt128.MAX_VALUE.toString());
    assertEquals("10", UInt128.TEN.toString());

    assertEquals("12345678", UInt128.from(12345678L).toString());
    assertEquals("18446744073697205938", UInt128.from(-12345678L).toString());
    UInt128 maxPositive = UInt128.from(0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL);
    assertEquals(new BigInteger(maxPositive.toString()).toString(), maxPositive.toString());
    UInt128 maxNegative = UInt128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L);
    assertEquals(new BigInteger(maxNegative.toString()).toString(), maxNegative.toString());
  }

  /** Test materialising {@code UInt128} values from {@code String} values. */
  @Test
  public void testFromString() {
    testRoundTrip("0");
    testRoundTrip("123456789");
    testRoundTrip("123456789123456789");
    testRoundTrip("123456789123456789123456789123456789");
    assertEquals(
        UInt128.from(0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL),
        UInt128.from(BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE).toString()));
    assertEquals(
        UInt128.from(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L),
        UInt128.from(BigInteger.ONE.shiftLeft(127).toString()));
  }

  /** Test increment. */
  @Test
  public void testIncrement() {
    assertEquals(UInt128.ONE, UInt128.ZERO.increment());
    assertEquals(UInt128.ZERO, UInt128.MAX_VALUE.increment()); // wrap
    assertEquals(UInt128.ONE.shiftLeft(Integer.SIZE), UInt128.from(0, 0, 0, -1).increment());
    assertEquals(UInt128.ONE.shiftLeft(Integer.SIZE * 2), UInt128.from(0, 0, -1, -1).increment());
    assertEquals(UInt128.ONE.shiftLeft(Integer.SIZE * 3), UInt128.from(0, -1, -1, -1).increment());
  }

  /** Test decrement. */
  @Test
  public void testDecrement() {
    assertEquals(UInt128.ZERO, UInt128.ONE.decrement());
    assertEquals(UInt128.MAX_VALUE, UInt128.ZERO.decrement()); // wrap
    assertEquals(UInt128.from(0, 0, 0, -1), UInt128.ONE.shiftLeft(Integer.SIZE).decrement());
    assertEquals(UInt128.from(0, 0, -1, -1), UInt128.ONE.shiftLeft(Integer.SIZE * 2).decrement());
    assertEquals(UInt128.from(0, -1, -1, -1), UInt128.ONE.shiftLeft(Integer.SIZE * 3).decrement());
    // Failure case found during review
    assertEquals(UInt128.from(-1, -1, 0, -1), UInt128.from(-1, -1, 1, 0).decrement());
  }

  /** Tests bitwise inversion. */
  @Test
  public void testInvert() {
    assertEquals(UInt128.MAX_VALUE, UInt128.ZERO.invert());
    assertEquals(UInt128.ZERO, UInt128.MAX_VALUE.invert());
    assertEquals(UInt128.MAX_VALUE.decrement(), UInt128.ONE.invert());
    assertEquals(UInt128.ONE, UInt128.MAX_VALUE.decrement().invert());
    assertEquals(UInt128.from(Long.MAX_VALUE, -1L), UInt128.from(Long.MIN_VALUE, 0L).invert());
    assertEquals(UInt128.from(Long.MIN_VALUE, 0L), UInt128.from(Long.MAX_VALUE, -1L).invert());
  }

  /** Test getLowestSetBit. */
  @Test
  public void testGetLowestSetBit() {
    assertEquals(-1, UInt128.ZERO.getLowestSetBit());
    assertEquals(0, UInt128.ONE.getLowestSetBit());
    assertEquals(1, UInt128.TWO.getLowestSetBit());
    assertEquals(2, UInt128.FOUR.getLowestSetBit());
    assertEquals(3, UInt128.EIGHT.getLowestSetBit());
    assertEquals(64, UInt128.from(1, 0).getLowestSetBit());
    assertEquals(65, UInt128.from(2, 0).getLowestSetBit());
    assertEquals(66, UInt128.from(4, 0).getLowestSetBit());
    assertEquals(67, UInt128.from(8, 0).getLowestSetBit());
  }

  @Test
  public void equalsContract() {
    EqualsVerifier.forClass(UInt128.class).verify();
  }

  /** Test div 0. */
  @Test(expected = IllegalArgumentException.class)
  public void testDiv0() {
    UInt128.ONE.divide(UInt128.ZERO);
  }

  /** Test rem 0. */
  @Test(expected = IllegalArgumentException.class)
  public void testRem0() {
    UInt128.ONE.remainder(UInt128.ZERO);
  }

  /** NumberFormatException on empty string. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionOnEmpty() {
    UInt128.from("");
  }

  /** NumberFormatException if no actual number. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionIfNoNumber() {
    UInt128.from("+");
  }

  /** NumberFormatException if invalid digit. */
  @Test(expected = NumberFormatException.class)
  public void numberFormatExceptionIfInvalidDigit() {
    UInt128.from("+a");
  }

  /** IllegalArgumentException if byte array is empty. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionIfByteArrayEmpty() {
    UInt128.from(new byte[0]);
  }

  /** IllegalArgumentException on radix too big. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnRadixTooBig() {
    UInt128.ONE.toString(Character.MAX_RADIX + 1);
  }

  /** IllegalArgumentException on radix too small. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnRadixTooSmall() {
    UInt128.ONE.toString(Character.MIN_RADIX - 1);
  }

  /** IllegalArgumentException on negative exponent for pow. */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentExceptionOnNegativeExponent() {
    UInt128.ONE.pow(-1);
  }

  private static void testRoundTrip(String s) {
    assertEquals(s, UInt128.from(s).toString());
  }
}
