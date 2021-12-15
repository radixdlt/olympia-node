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

/** Utility methods for working with various UInt classes. */
public final class UIntUtils {

  private static final String OVERFLOW = "overflow";
  private static final String UNDERFLOW = "underflow";

  // Values taken from
  // https://en.wikipedia.org/wiki/Double-precision_floating-point_format
  private static final int SIGNIFICAND_PREC = 53; // Including implicit leading one bit
  private static final long SIGNIFICAND_MASK = (1L << (SIGNIFICAND_PREC - 1)) - 1L;
  private static final long SIGNIFICAND_OVF = 1L << SIGNIFICAND_PREC;

  private static final int EXPONENT_BIAS = 1023;

  private UIntUtils() {
    throw new IllegalStateException("Can't construct");
  }

  /**
   * Compute {@code addend + augend}, throwing an exception if overflow occurs.
   *
   * @param addend The addend
   * @param augend The augend
   * @return {@code addend + augend} if no overflow
   * @throws ArithmeticException if an overflow occurs
   */
  public static UInt256 addWithOverflow(UInt256 addend, UInt256 augend) {
    UInt256 maxAddend = UInt256.MAX_VALUE.subtract(augend);
    if (maxAddend.compareTo(addend) < 0) {
      throw new ArithmeticException(OVERFLOW);
    }
    return addend.add(augend);
  }

  /**
   * Compute {@code addend + augend}, throwing an exception if overflow occurs.
   *
   * @param addend The addend
   * @param augend The augend
   * @return {@code addend + augend} if no overflow
   * @throws ArithmeticException if an overflow occurs
   */
  public static UInt384 addWithOverflow(UInt384 addend, UInt256 augend) {
    UInt384 maxAddend = UInt384.MAX_VALUE.subtract(augend);
    if (maxAddend.compareTo(addend) < 0) {
      throw new ArithmeticException(OVERFLOW);
    }
    return addend.add(augend);
  }

  /**
   * Compute {@code addend + augend}, throwing an exception if overflow occurs.
   *
   * @param addend The addend
   * @param augend The augend
   * @return {@code addend + augend} if no overflow
   * @throws ArithmeticException if an overflow occurs
   */
  public static UInt384 addWithOverflow(UInt384 addend, UInt384 augend) {
    UInt384 maxAddend = UInt384.MAX_VALUE.subtract(augend);
    if (maxAddend.compareTo(addend) < 0) {
      throw new ArithmeticException(OVERFLOW);
    }
    return addend.add(augend);
  }

  /**
   * Compute {@code minuend - subtrahend}, throwing an exception if underflow occurs.
   *
   * @param addend The minuend
   * @param augend The subtrahend
   * @return {@code minuend - subtrahend} if no overflow
   * @throws ArithmeticException if an underflow occurs
   */
  public static UInt256 subtractWithUnderflow(UInt256 minuend, UInt256 subtrahend) {
    UInt256 minMinuend = UInt256.MIN_VALUE.add(minuend);
    if (minMinuend.compareTo(subtrahend) < 0) {
      throw new ArithmeticException(UNDERFLOW);
    }
    return minuend.subtract(subtrahend);
  }

  /**
   * Compute {@code minuend - subtrahend}, throwing an exception if underflow occurs.
   *
   * @param addend The minuend
   * @param augend The subtrahend
   * @return {@code minuend - subtrahend} if no overflow
   * @throws ArithmeticException if an underflow occurs
   */
  public static UInt384 subtractWithUnderflow(UInt384 minuend, UInt256 subtrahend) {
    UInt384 minMinuend = UInt384.MIN_VALUE.add(minuend);
    if (minMinuend.compareTo(UInt384.from(subtrahend)) < 0) {
      throw new ArithmeticException(UNDERFLOW);
    }
    return minuend.subtract(subtrahend);
  }

  /**
   * Compute {@code minuend - subtrahend}, throwing an exception if underflow occurs.
   *
   * @param addend The minuend
   * @param augend The subtrahend
   * @return {@code minuend - subtrahend} if no overflow
   * @throws ArithmeticException if an underflow occurs
   */
  public static UInt384 subtractWithUnderflow(UInt384 minuend, UInt384 subtrahend) {
    UInt384 minMinuend = UInt384.MIN_VALUE.add(minuend);
    if (minMinuend.compareTo(subtrahend) < 0) {
      throw new ArithmeticException(UNDERFLOW);
    }
    return minuend.subtract(subtrahend);
  }

  /**
   * Convert {@link UInt128} to an approximate {@code double} value.
   *
   * @param x the value to convert
   * @return the value as a {@code double}
   */
  public static double toDouble(UInt128 x) {
    long h = x.getHigh();
    long l = x.getLow();

    // If it's a number that fits into a long, let the compiler convert.
    if (h == 0L && l >= 0L) {
      return l;
    }

    // Must be at least 64 bits based on initial checks.  Note that it is
    // not possible for this exponent to overflow a double (128 < 1023).
    int shift = bitLength(h);
    long exponent = Long.SIZE + shift - 1L;

    // Merge all the bits into l, discarding lower bits
    l >>>= shift;
    h <<= Long.SIZE - shift;
    l |= h;

    // Extract 53 bits of significand. Note that we make a
    // quick stop part way through to organise rounding.
    // Note that rounding is approximate, not RTNE.
    l >>>= Long.SIZE - SIGNIFICAND_PREC - 1;
    l += 1;
    l >>>= 1;

    // If rounding has caused overflow, then shift an extra bit
    if ((l & SIGNIFICAND_OVF) != 0L) {
      exponent += 1;
      l >>>= 1;
    }

    // Assemble into a double now.
    long raw = (exponent + EXPONENT_BIAS) << (SIGNIFICAND_PREC - 1);
    raw |= l & SIGNIFICAND_MASK;
    return Double.longBitsToDouble(raw);
  }

  private static int bitLength(long n) {
    return Long.SIZE - Long.numberOfLeadingZeros(n);
  }
}
