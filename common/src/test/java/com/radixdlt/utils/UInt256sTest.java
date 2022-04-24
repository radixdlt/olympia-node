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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.Test;

public class UInt256sTest {
  @Test
  public void when_constructing_uint256_from_big_integer__values_compare_equal() {
    for (int pow2 = 0; pow2 <= 255; pow2++) {
      assertEquals(UInt256s.fromBigInteger(BigInteger.valueOf(2).pow(pow2)), UInt256.TWO.pow(pow2));
    }
  }

  @Test
  public void when_constructing_uint256_from_negative_big_integer__exception_is_thrown() {
    assertThatThrownBy(() -> UInt256s.fromBigInteger(BigInteger.valueOf(-1L)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void when_constructing_uint256_from_too_large_biginteger__exception_is_thrown() {
    BigInteger tooBig = BigInteger.valueOf(2).pow(256);
    assertThatThrownBy(() -> UInt256s.fromBigInteger(tooBig))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void when_constructing_uint256_from_bigdecimal__values_compare_equal() {
    for (int pow2 = 0; pow2 <= 255; pow2++) {
      assertEquals(UInt256s.fromBigDecimal(BigDecimal.valueOf(2).pow(pow2)), UInt256.TWO.pow(pow2));
    }
  }

  @Test
  public void when_constructing_uint256_from_negative_bigdecimal__exception_is_thrown() {
    assertThatThrownBy(() -> UInt256s.fromBigDecimal(BigDecimal.valueOf(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void when_constructing_uint256_from_too_large_bigdecimal__exception_is_thrown() {
    BigDecimal tooBig = BigDecimal.valueOf(2).pow(256);
    assertThatThrownBy(() -> UInt256s.fromBigDecimal(tooBig))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void when_constructing_uint256_from_fractional_bigdecimal__exception_is_thrown() {
    BigDecimal fractional = BigDecimal.valueOf(Math.PI);
    assertThatThrownBy(() -> UInt256s.fromBigDecimal(fractional))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void test_min_cases() {
    assertEquals(UInt256.ZERO, UInt256s.min(UInt256.ONE, UInt256.ZERO));
    assertEquals(UInt256.ZERO, UInt256s.min(UInt256.ZERO, UInt256.ONE));

    assertEquals(UInt256.TEN, UInt256s.min(UInt256.MAX_VALUE, UInt256.TEN));
    assertEquals(UInt256.TEN, UInt256s.min(UInt256.TEN, UInt256.MAX_VALUE));

    assertEquals(UInt256.ONE, UInt256s.min(UInt256.ONE, UInt256.ONE));
  }

  @Test
  public void test_max_cases() {
    assertEquals(UInt256.ONE, UInt256s.max(UInt256.ONE, UInt256.ZERO));
    assertEquals(UInt256.ONE, UInt256s.max(UInt256.ZERO, UInt256.ONE));

    assertEquals(UInt256.MAX_VALUE, UInt256s.max(UInt256.MAX_VALUE, UInt256.TEN));
    assertEquals(UInt256.MAX_VALUE, UInt256s.max(UInt256.TEN, UInt256.MAX_VALUE));

    assertEquals(UInt256.ONE, UInt256s.max(UInt256.ONE, UInt256.ONE));
  }

  @Test
  public void lcm_edgecases() {
    // Null cap
    assertThatThrownBy(() -> UInt256s.cappedLCM(null, UInt256.ONE, UInt256.TWO))
        .isInstanceOf(NullPointerException.class);

    // Empty varargs
    assertThatThrownBy(() -> UInt256s.cappedLCM(UInt256.MAX_VALUE))
        .isInstanceOf(ArrayIndexOutOfBoundsException.class);

    // Exceeds cap
    assertNull(UInt256s.cappedLCM(UInt256.ONE, UInt256.TWO, UInt256.THREE));

    // Zero element
    assertEquals(UInt256.ZERO, UInt256s.cappedLCM(UInt256.MAX_VALUE, UInt256.ONE, UInt256.ZERO));

    // Overflow - lcm of two largest primes less than UInt256.MAX_VALUE
    UInt256 largePrime1 =
        UInt256s.fromBigInteger(
            new BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639747"));
    UInt256 largePrime2 =
        UInt256s.fromBigInteger(
            new BigInteger(
                "115792089237316195423570985008687907853269984665640564039457584007913129639579"));
    assertNull(UInt256s.cappedLCM(UInt256.MAX_VALUE, largePrime1, largePrime2));
  }

  private static <T> void testPrimeLCM(
      IntFunction<T[]> arrayBuilder,
      LongFunction<T> builder,
      BinaryOperator<T> multiply,
      T one,
      T max,
      Function<T, BigInteger> toBI,
      BiFunction<T, T[], T> lcmFunction) {
    List<Integer> primeList =
        Arrays.asList(
            2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83,
            89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149);
    IntFunction<LongStream> primeNumbers = size -> primeList.stream().mapToLong(i -> i).limit(size);

    BigInteger maxBI = toBI.apply(max);
    for (int size = 1; size < primeList.size(); size++) {
      T[] values = primeNumbers.apply(size).mapToObj(builder).toArray(arrayBuilder);

      BigInteger biExpected =
          primeNumbers
              .apply(size)
              .mapToObj(BigInteger::valueOf)
              .reduce(BigInteger.ONE, BigInteger::multiply);

      final T expected;
      if (biExpected.compareTo(maxBI) > 0) {
        expected = null;
      } else {
        expected = primeNumbers.apply(size).mapToObj(builder).reduce(one, multiply);
      }

      assertThat(lcmFunction.apply(max, values)).isEqualTo(expected);
    }
  }

  private static <T> void testGeometricLCM(
      IntFunction<T[]> arrayBuilder,
      LongFunction<T> builder,
      BinaryOperator<T> multiply,
      BiFunction<T, Integer, T> pow,
      T max,
      Function<T, BigInteger> toBI,
      BiFunction<T, T[], T> lcmFunction) {

    BigInteger maxBI = toBI.apply(max);
    for (int exponent = 1; exponent < 12; exponent++) {
      for (int base = 2; base < 1024; base++) {
        final T baseNum = builder.apply(base);
        final T[] values =
            Stream.iterate(baseNum, n -> multiply.apply(n, baseNum))
                .limit(exponent)
                .toArray(arrayBuilder);

        final BigInteger biExpected = BigInteger.valueOf(base).pow(exponent);
        final T expected;
        if (biExpected.compareTo(maxBI) > 0) {
          expected = null;
        } else {
          expected = pow.apply(builder.apply(base), exponent);
        }

        assertThat(lcmFunction.apply(max, values)).isEqualTo(expected);
      }
    }
  }

  @Test
  public void testOneAndMax256() {
    assertThat(UInt256s.cappedLCM(UInt256.MAX_VALUE, UInt256.ONE, UInt256.MAX_VALUE))
        .isEqualTo(UInt256.MAX_VALUE);
  }

  @Test
  public void testPrimeLCM256() {
    testPrimeLCM(
        UInt256[]::new,
        UInt256::from,
        UInt256::multiply,
        UInt256.ONE,
        UInt256.MAX_VALUE,
        i -> new BigInteger(1, i.toByteArray()),
        UInt256s::cappedLCM);
  }

  @Test
  public void testGeometricLCM256() {
    testGeometricLCM(
        UInt256[]::new,
        UInt256::from,
        UInt256::multiply,
        UInt256::pow,
        UInt256.MAX_VALUE,
        i -> new BigInteger(1, i.toByteArray()),
        UInt256s::cappedLCM);
  }
}
