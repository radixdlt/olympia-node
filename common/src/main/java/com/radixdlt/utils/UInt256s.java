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

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/** Utility methods for converting to/from {@link UInt256}. */
@SecurityCritical(SecurityKind.NUMERIC)
public final class UInt256s {
  private UInt256s() {
    throw new IllegalStateException("Can't construct");
  }

  private static final BigInteger MAX_UINT256 = toBigInteger(UInt256.MAX_VALUE);

  /**
   * Returns the specified {@link UInt256} as a {@link BigInteger}.
   *
   * @param value The value to convert
   * @return The value as a {@link BigInteger}
   */
  public static BigInteger toBigInteger(UInt256 value) {
    // Set sign to positive to stop BigInteger interpreting high bit as sign
    return new BigInteger(1, value.toByteArray());
  }

  /**
   * Returns the specified {@link BigInteger} as a {@link UInt256}.
   *
   * @param value The value to convert
   * @return The value as a {@link UInt256}
   * @throws IllegalArgumentException if {@code value} &lt; 0, or {@code value} &gt; {@link
   *     UInt256#MAX_VALUE}.
   */
  public static UInt256 fromBigInteger(BigInteger value) {
    if (value.signum() < 0) {
      throw new IllegalArgumentException("value must be >= 0: " + value);
    }

    if (value.compareTo(MAX_UINT256) > 0) {
      throw new IllegalArgumentException("value must be <= " + MAX_UINT256 + ": " + value);
    }

    final byte[] byteArray = value.toByteArray();

    // Handle possible padded zeroes
    if (byteArray.length > UInt256.BYTES) {
      return UInt256.from(byteArray, byteArray.length - UInt256.BYTES);
    } else {
      return UInt256.from(byteArray);
    }
  }

  /**
   * Returns the specified {@link BigDecimal} as a {@link UInt256}.
   *
   * @param value The value to convert
   * @return The value as a {@link UInt256}
   * @throws ArithmeticException if {@code value} has a nonzero fractional part.
   * @throws IllegalArgumentException if {@code value} &lt; 0, or {@code value} &gt; {@link
   *     UInt256#MAX_VALUE}.
   */
  public static UInt256 fromBigDecimal(BigDecimal value) {
    return fromBigInteger(value.toBigIntegerExact());
  }

  /**
   * Returns the smaller of two {@code UInt256} values. That is, the result the argument closer to
   * the value of {@link UInt256#MIN_VALUE}. If the arguments have the same value, the result is
   * that same value.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the smaller of {@code a} and {@code b}.
   */
  public static UInt256 min(UInt256 a, UInt256 b) {
    int cmp = a.compareTo(b);
    return (cmp <= 0) ? a : b;
  }

  /**
   * Returns the larger of two {@code UInt256} values. That is, the result the argument closer to
   * the value of {@link UInt256#MAX_VALUE}. If the arguments have the same value, the result is
   * that same value.
   *
   * @param a an argument.
   * @param b another argument.
   * @return the smaller of {@code a} and {@code b}.
   */
  public static UInt256 max(UInt256 a, UInt256 b) {
    int cmp = a.compareTo(b);
    return (cmp >= 0) ? a : b;
  }

  /**
   * Euclid's algorithm to find greatest common divisor.
   *
   * @param x first number
   * @param y second number
   * @return greatest common divisor between x and y
   */
  private static UInt256 gcd(UInt256 x, UInt256 y) {
    return (y.isZero()) ? x : gcd(y, x.remainder(y));
  }

  /**
   * Least common multiple computed via reduction by gcd.
   *
   * @param x first number
   * @param y second number
   * @return least common multiple between x and y, or {@code null} if overflow
   */
  private static UInt256 lcm(UInt256 x, UInt256 y) {
    UInt256 d = y.divide(gcd(x, y));
    UInt256 r = x.multiply(d);
    boolean overflow = !x.isZero() && !r.divide(x).equals(d);
    return overflow ? null : r;
  }

  /**
   * Returns a capped least common multiple between an array of {@link UInt256} numbers. The cap
   * acts as a ceiling. If the result exceeds cap, then this will return null.
   *
   * <p>All numbers must be non-null and non-zero. Otherwise, result is undefined.
   *
   * @param cap the cap to be used for the computation
   * @param numbers array of numbers of size at least 1
   * @return null, if the least common multiple is greater than cap, otherwise the least common
   *     multiple
   * @throws ArrayIndexOutOfBoundsException if numbers is a zero length array
   * @throws NullPointerException if cap or numbers is null
   */
  public static UInt256 cappedLCM(UInt256 cap, UInt256... numbers) {
    Objects.requireNonNull(cap);
    UInt256 r = numbers[0];
    for (int i = 1; i < numbers.length; i++) {
      r = lcm(r, numbers[i]);
      if (r == null || r.compareTo(cap) > 0) {
        return null;
      }
    }
    return r;
  }
}
