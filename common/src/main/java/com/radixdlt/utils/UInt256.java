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

import static com.radixdlt.errors.ApiErrors.UNABLE_TO_PARSE_UINT;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.utils.functional.Result;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/** A 256-bit unsigned integer, with comparison and some basic arithmetic operations. */
@SecurityCritical(SecurityKind.NUMERIC)
public final class UInt256 implements Comparable<UInt256>, Serializable {
  // Some sizing constants in line with Integer, Long etc
  /** Size of this numeric type in bits. */
  public static final int SIZE = UInt128.SIZE * 2;

  /** Size of this numeric type in bytes. */
  public static final int BYTES = UInt128.BYTES * 2;

  /** A constant holding the minimum value an {@code Int256} can have, 0. */
  public static final UInt256 MIN_VALUE = new UInt256(UInt128.ZERO, UInt128.ZERO);

  /** Highest bit. */
  public static final UInt256 HIGH_BIT = new UInt256(UInt128.HIGH_BIT, UInt128.ZERO);

  /** A constant holding the maximum value an {@code Int256} can have, 2<sup>256</sup>-1. */
  public static final UInt256 MAX_VALUE = new UInt256(UInt128.MAX_VALUE, UInt128.MAX_VALUE);

  // Some commonly used values
  public static final UInt256 ZERO = new UInt256(UInt128.ZERO, UInt128.ZERO);
  public static final UInt256 ONE = new UInt256(UInt128.ZERO, UInt128.ONE);
  public static final UInt256 TWO = new UInt256(UInt128.ZERO, UInt128.TWO);
  public static final UInt256 THREE = new UInt256(UInt128.ZERO, UInt128.THREE);
  public static final UInt256 FOUR = new UInt256(UInt128.ZERO, UInt128.FOUR);
  public static final UInt256 FIVE = new UInt256(UInt128.ZERO, UInt128.FIVE);
  public static final UInt256 SIX = new UInt256(UInt128.ZERO, UInt128.SIX);
  public static final UInt256 SEVEN = new UInt256(UInt128.ZERO, UInt128.SEVEN);
  public static final UInt256 EIGHT = new UInt256(UInt128.ZERO, UInt128.EIGHT);
  public static final UInt256 NINE = new UInt256(UInt128.ZERO, UInt128.NINE);
  public static final UInt256 TEN = new UInt256(UInt128.ZERO, UInt128.TEN);

  // Numbers in order.  This is used by factory methods.
  private static final UInt256[] numbers = {
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
  };

  // Mask of int bits as a long
  private static final long INT_MASK = (1L << Integer.SIZE) - 1L;

  // The actual value.
  // @PackageLocalForTest
  final UInt128 high;
  // @PackageLocalForTest
  final UInt128 low;

  /**
   * Factory method for materialising an {@link UInt256} from a {@code short} value.
   *
   * @param value The value to be represented as an {@link UInt256}.
   * @return {@code value} as an {@link UInt256} type.
   */
  public static UInt256 from(short value) {
    return from(value & 0xFFFF);
  }

  /**
   * Factory method for materialising an {@link UInt256} from an {@code int} value.
   *
   * @param value The value to be represented as an {@link UInt256}.
   * @return {@code value} as an {@link UInt256} type.
   */
  public static UInt256 from(int value) {
    return from(value & 0xFFFF_FFFFL);
  }

  /**
   * Factory method for materialising an {@link UInt256} from a {@code long} value. Note that values
   * are zero extended into the 256 bit value.
   *
   * @param value The value to be represented as an {@link UInt256}.
   * @return {@code value} as an {@link UInt256} type.
   */
  public static UInt256 from(long value) {
    return from(UInt128.from(value));
  }

  /**
   * Factory method for materialising an {@link UInt256} from an {@link UInt128} value. Note that
   * values are zero extended into the 256 bit value.
   *
   * @param value The least significant word of the value.
   * @return the specified value as an {@link UInt256} type.
   */
  public static UInt256 from(UInt128 value) {
    return from(UInt128.ZERO, value);
  }

  /**
   * Factory method for materialising an {@link UInt256} from an {@link UInt128} value.
   *
   * @param high The most significant word of the value.
   * @param low The least significant word of the value.
   * @return the specified values as an {@link UInt256} type.
   */
  public static UInt256 from(UInt128 high, UInt128 low) {
    return new UInt256(high, low);
  }

  /**
   * Factory method for materialising an {@link UInt256} from an array of bytes. The array is
   * most-significant byte first, and must not be zero length.
   *
   * <p>If the array is smaller than {@link #BYTES}, then it is effectively padded with leading zero
   * bytes.
   *
   * <p>If the array is longer than {@link #BYTES}, then values at index {@link #BYTES} and beyond
   * are ignored.
   *
   * @param bytes The array of bytes to be used.
   * @return {@code bytes} as an {@link UInt256} type.
   * @throws IllegalArgumentException if {@code bytes} is 0 length.
   * @see #toByteArray()
   */
  public static UInt256 from(byte[] bytes) {
    Objects.requireNonNull(bytes);
    if (bytes.length == 0) {
      throw new IllegalArgumentException("bytes is 0 bytes long");
    }
    byte[] newBytes = extend(bytes);
    return from(newBytes, 0);
  }

  /**
   * Factory method for materialising an {@link UInt256} from an array of bytes. The array is
   * most-significant byte first.
   *
   * @param bytes The array of bytes to be used.
   * @param offset The offset within the array to be used.
   * @return {@code bytes} from {@code offset} as an {@link UInt256} type.
   * @see #toByteArray()
   */
  public static UInt256 from(byte[] bytes, int offset) {
    UInt128 high = UInt128.from(bytes, offset);
    UInt128 low = UInt128.from(bytes, offset + UInt128.BYTES);
    return from(high, low);
  }

  /**
   * Factory method for materialising an {@link UInt256} from a string. Conversion is performed base
   * 10 and leading '+' sign character is permitted.
   *
   * @param s The array of bytes to be used.
   * @return {@code s} as an {@link UInt256} type.
   * @throws NumberFormatException if {@code s} is not a valid integer number.
   */
  @JsonCreator
  public static UInt256 from(String s) {
    Objects.requireNonNull(s);

    int len = s.length();
    if (len > 0) {
      int i = 0;
      char ch = s.charAt(0);
      if (ch == '+') {
        i += 1; // skip first char
      }
      if (i >= len) {
        throw new NumberFormatException(s);
      }
      // No real effort to catch overflow here
      UInt256 result = UInt256.ZERO;
      while (i < len) {
        int digit = Character.digit(s.charAt(i++), 10);
        if (digit < 0) {
          throw new NumberFormatException(s);
        }
        result = result.multiply(UInt256.TEN).add(numbers[digit]);
      }
      return result;
    } else {
      throw new NumberFormatException(s);
    }
  }

  /**
   * Functional style friendly version of {@link #from(String)}. Instead of throwing exceptions this
   * method returns {@link Result}.
   *
   * @param input The string to parse
   * @return Success {@link Result} if value can be parsed and failure {@link Result} otherwise.
   */
  public static Result<UInt256> fromString(String input) {
    return Result.wrap(() -> UNABLE_TO_PARSE_UINT.with(input), () -> from(input));
  }

  // Pad short (< BYTES length) array with appropriate lead bytes.
  private static byte[] extend(byte[] bytes) {
    if (bytes.length >= BYTES) {
      return bytes;
    }
    byte[] newBytes = new byte[BYTES];
    int newPos = BYTES - bytes.length;
    Arrays.fill(newBytes, 0, newPos, (byte) 0);
    System.arraycopy(bytes, 0, newBytes, newPos, bytes.length);
    return newBytes;
  }

  private UInt256(UInt128 high, UInt128 low) {
    this.high = Objects.requireNonNull(high);
    this.low = Objects.requireNonNull(low);
  }

  @VisibleForTesting
  UInt256(byte[] bytes) {
    int[] ints = new int[BYTES / Integer.BYTES];
    int intIndex = ints.length;
    int intLen = Math.min(bytes.length, Integer.BYTES);
    int byteIndex = bytes.length - intLen;
    while (intIndex > 0 && intLen > 0) {
      ints[--intIndex] = Ints.fromByteArray(bytes, byteIndex, intLen);
      intLen = Math.min(byteIndex, Integer.BYTES);
      byteIndex -= intLen;
    }
    this.high = UInt128.from(ints[0], ints[1], ints[2], ints[3]);
    this.low = UInt128.from(ints[4], ints[5], ints[6], ints[7]);
  }

  /**
   * Converts {@code this} to an array of bytes. The most significant byte will be returned in index
   * zero. The array will always be {@link #BYTES} bytes long, and will be zero filled to suit the
   * actual value.
   *
   * @return An array of {@link #BYTES} bytes representing the value of this {@link UInt256}.
   */
  public byte[] toByteArray() {
    return toByteArray(new byte[BYTES], 0);
  }

  /**
   * Converts {@code this} to an array of bytes. The most significant byte will be returned in index
   * {@code offset}. The array must be at least {@code offset + BYTES} long.
   *
   * @param bytes The array to place the bytes in.
   * @param offset The offset within the array to place the bytes.
   * @return The passed-in value of {@code bytes}.
   */
  public byte[] toByteArray(byte[] bytes, int offset) {
    this.high.toByteArray(bytes, offset);
    this.low.toByteArray(bytes, offset + UInt128.BYTES);
    return bytes;
  }

  /**
   * Adds {@code other} to {@code this}, returning the result.
   *
   * @param other The addend.
   * @return An {@link UInt256} with the value {@code this + other}.
   */
  public UInt256 add(UInt256 other) {
    long partial0 = (this.low.low & INT_MASK) + (other.low.low & INT_MASK);
    long partial1 =
        (this.low.midLow & INT_MASK) + (other.low.midLow & INT_MASK) + (partial0 >>> Integer.SIZE);
    long partial2 =
        (this.low.midHigh & INT_MASK)
            + (other.low.midHigh & INT_MASK)
            + (partial1 >>> Integer.SIZE);
    long partial3 =
        (this.low.high & INT_MASK) + (other.low.high & INT_MASK) + (partial2 >>> Integer.SIZE);
    long partial4 =
        (this.high.low & INT_MASK) + (other.high.low & INT_MASK) + (partial3 >>> Integer.SIZE);
    long partial5 =
        (this.high.midLow & INT_MASK)
            + (other.high.midLow & INT_MASK)
            + (partial4 >>> Integer.SIZE);
    long partial6 =
        (this.high.midHigh & INT_MASK)
            + (other.high.midHigh & INT_MASK)
            + (partial5 >>> Integer.SIZE);
    long partial7 =
        (this.high.high & INT_MASK) + (other.high.high & INT_MASK) + (partial6 >>> Integer.SIZE);
    return UInt256.from(
        UInt128.from((int) partial7, (int) partial6, (int) partial5, (int) partial4),
        UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0));
  }

  /**
   * Adds {@code other} to {@code this}, returning the result.
   *
   * @param other The addend.
   * @return An {@link UInt256} with the value {@code this + other}.
   */
  public UInt256 add(UInt128 other) {
    long partial0 = (this.low.low & INT_MASK) + (other.low & INT_MASK);
    long partial1 =
        (this.low.midLow & INT_MASK) + (other.midLow & INT_MASK) + (partial0 >>> Integer.SIZE);
    long partial2 =
        (this.low.midHigh & INT_MASK) + (other.midHigh & INT_MASK) + (partial1 >>> Integer.SIZE);
    long partial3 =
        (this.low.high & INT_MASK) + (other.high & INT_MASK) + (partial2 >>> Integer.SIZE);
    long partial4 = (this.high.low & INT_MASK) + (partial3 >>> Integer.SIZE);
    long partial5 = (this.high.midLow & INT_MASK) + (partial4 >>> Integer.SIZE);
    long partial6 = (this.high.midHigh & INT_MASK) + (partial5 >>> Integer.SIZE);
    long partial7 = (this.high.high & INT_MASK) + (partial6 >>> Integer.SIZE);
    return UInt256.from(
        UInt128.from((int) partial7, (int) partial6, (int) partial5, (int) partial4),
        UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0));
  }

  /**
   * Subtracts {@code other} from {@code this}, returning the result.
   *
   * @param other The subtrahend.
   * @return An {@link UInt256} with the value {@code this - other}.
   */
  public UInt256 subtract(UInt256 other) {
    long partial0 = (this.low.low & INT_MASK) - (other.low.low & INT_MASK);
    long partial1 =
        (this.low.midLow & INT_MASK) - (other.low.midLow & INT_MASK) + (partial0 >> Integer.SIZE);
    long partial2 =
        (this.low.midHigh & INT_MASK) - (other.low.midHigh & INT_MASK) + (partial1 >> Integer.SIZE);
    long partial3 =
        (this.low.high & INT_MASK) - (other.low.high & INT_MASK) + (partial2 >> Integer.SIZE);
    long partial4 =
        (this.high.low & INT_MASK) - (other.high.low & INT_MASK) + (partial3 >> Integer.SIZE);
    long partial5 =
        (this.high.midLow & INT_MASK) - (other.high.midLow & INT_MASK) + (partial4 >> Integer.SIZE);
    long partial6 =
        (this.high.midHigh & INT_MASK)
            - (other.high.midHigh & INT_MASK)
            + (partial5 >> Integer.SIZE);
    long partial7 =
        (this.high.high & INT_MASK) - (other.high.high & INT_MASK) + (partial6 >> Integer.SIZE);
    return UInt256.from(
        UInt128.from((int) partial7, (int) partial6, (int) partial5, (int) partial4),
        UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0));
  }

  /**
   * Subtracts {@code other} from {@code this}, returning the result.
   *
   * @param other The subtrahend.
   * @return An {@link UInt256} with the value {@code this - other}.
   */
  public UInt256 subtract(UInt128 other) {
    long partial0 = (this.low.low & INT_MASK) - (other.low & INT_MASK);
    long partial1 =
        (this.low.midLow & INT_MASK) - (other.midLow & INT_MASK) + (partial0 >> Integer.SIZE);
    long partial2 =
        (this.low.midHigh & INT_MASK) - (other.midHigh & INT_MASK) + (partial1 >> Integer.SIZE);
    long partial3 =
        (this.low.high & INT_MASK) - (other.high & INT_MASK) + (partial2 >> Integer.SIZE);
    long partial4 = (this.high.low & INT_MASK) + (partial3 >> Integer.SIZE);
    long partial5 = (this.high.midLow & INT_MASK) + (partial4 >> Integer.SIZE);
    long partial6 = (this.high.midHigh & INT_MASK) + (partial5 >> Integer.SIZE);
    long partial7 = (this.high.high & INT_MASK) + (partial6 >> Integer.SIZE);
    return UInt256.from(
        UInt128.from((int) partial7, (int) partial6, (int) partial5, (int) partial4),
        UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0));
  }

  /**
   * Increments {@code this}. Equivalent to {@code this.add(Int256.ONE)}, but faster.
   *
   * @return This number incremented by one.
   */
  public UInt256 increment() {
    UInt128 l = this.low.increment();
    UInt128 h = l.isZero() ? this.high.increment() : this.high;
    return UInt256.from(h, l);
  }

  /**
   * Decrements {@code this}. Equivalent to {@code this.subtract(Int256.ONE)}, but faster.
   *
   * @return This number decremented by one.
   */
  public UInt256 decrement() {
    UInt128 l = this.low.decrement();
    UInt128 h = this.low.isZero() ? this.high.decrement() : this.high;
    return UInt256.from(h, l);
  }

  /**
   * Multiplies {@code this} by the specified multiplicand.
   *
   * @param multiplicand The multiplicand to multiply {@code this} by.
   * @return The result {@code this * multiplicand}.
   */
  public UInt256 multiply(UInt256 multiplicand) {
    // I appreciate that this looks like a wall of code, and it is,
    // but the underlying algorithm is long multiplication base 2^32.

    long llowlow = (multiplicand.low.low & INT_MASK);
    long partial00 = (this.low.low & INT_MASK) * llowlow;
    long partial01 = (this.low.midLow & INT_MASK) * llowlow + (partial00 >>> Integer.SIZE);
    long partial02 = (this.low.midHigh & INT_MASK) * llowlow + (partial01 >>> Integer.SIZE);
    long partial03 = (this.low.high & INT_MASK) * llowlow + (partial02 >>> Integer.SIZE);
    long partial04 = (this.high.low & INT_MASK) * llowlow + (partial03 >>> Integer.SIZE);
    long partial05 = (this.high.midLow & INT_MASK) * llowlow + (partial04 >>> Integer.SIZE);
    long partial06 = (this.high.midHigh & INT_MASK) * llowlow + (partial05 >>> Integer.SIZE);
    long partial07 = (this.high.high & INT_MASK) * llowlow + (partial06 >>> Integer.SIZE);

    long llowmidlow = (multiplicand.low.midLow & INT_MASK);
    long partial10 = (this.low.low & INT_MASK) * llowmidlow;
    long partial11 = (this.low.midLow & INT_MASK) * llowmidlow + (partial10 >>> Integer.SIZE);
    long partial12 = (this.low.midHigh & INT_MASK) * llowmidlow + (partial11 >>> Integer.SIZE);
    long partial13 = (this.low.high & INT_MASK) * llowmidlow + (partial12 >>> Integer.SIZE);
    long partial14 = (this.high.low & INT_MASK) * llowmidlow + (partial13 >>> Integer.SIZE);
    long partial15 = (this.high.midLow & INT_MASK) * llowmidlow + (partial14 >>> Integer.SIZE);
    long partial16 = (this.high.midHigh & INT_MASK) * llowmidlow + (partial15 >>> Integer.SIZE);

    long llowmidhigh = (multiplicand.low.midHigh & INT_MASK);
    long partial20 = (this.low.low & INT_MASK) * llowmidhigh;
    long partial21 = (this.low.midLow & INT_MASK) * llowmidhigh + (partial20 >>> Integer.SIZE);
    long partial22 = (this.low.midHigh & INT_MASK) * llowmidhigh + (partial21 >>> Integer.SIZE);
    long partial23 = (this.low.high & INT_MASK) * llowmidhigh + (partial22 >>> Integer.SIZE);
    long partial24 = (this.high.low & INT_MASK) * llowmidhigh + (partial23 >>> Integer.SIZE);
    long partial25 = (this.high.midLow & INT_MASK) * llowmidhigh + (partial24 >>> Integer.SIZE);

    long llowhigh = (multiplicand.low.high & INT_MASK);
    long partial30 = (this.low.low & INT_MASK) * llowhigh;
    long partial31 = (this.low.midLow & INT_MASK) * llowhigh + (partial30 >>> Integer.SIZE);
    long partial32 = (this.low.midHigh & INT_MASK) * llowhigh + (partial31 >>> Integer.SIZE);
    long partial33 = (this.low.high & INT_MASK) * llowhigh + (partial32 >>> Integer.SIZE);
    long partial34 = (this.high.low & INT_MASK) * llowhigh + (partial33 >>> Integer.SIZE);

    long lhighlow = (multiplicand.high.low & INT_MASK);
    long partial40 = (this.low.low & INT_MASK) * lhighlow;
    long partial41 = (this.low.midLow & INT_MASK) * lhighlow + (partial40 >>> Integer.SIZE);
    long partial42 = (this.low.midHigh & INT_MASK) * lhighlow + (partial41 >>> Integer.SIZE);
    long partial43 = (this.low.high & INT_MASK) * lhighlow + (partial42 >>> Integer.SIZE);

    long lhighmidlow = (multiplicand.high.midLow & INT_MASK);
    long partial50 = (this.low.low & INT_MASK) * lhighmidlow;
    long partial51 = (this.low.midLow & INT_MASK) * lhighmidlow + (partial50 >>> Integer.SIZE);
    long partial52 = (this.low.midHigh & INT_MASK) * lhighmidlow + (partial51 >>> Integer.SIZE);

    long lhighmidhigh = (multiplicand.high.midHigh & INT_MASK);
    long partial60 = (this.low.low & INT_MASK) * lhighmidhigh;
    long partial61 = (this.low.midLow & INT_MASK) * lhighmidhigh + (partial60 >>> Integer.SIZE);

    long partial70 = (this.low.low & INT_MASK) * (multiplicand.high.high & INT_MASK);

    long i0 = (partial00 & INT_MASK);
    long i1 = (partial10 & INT_MASK) + (partial01 & INT_MASK);
    long i2 =
        (partial20 & INT_MASK)
            + (partial11 & INT_MASK)
            + (partial02 & INT_MASK)
            + (i1 >>> Integer.SIZE);
    long i3 =
        (partial30 & INT_MASK)
            + (partial21 & INT_MASK)
            + (partial12 & INT_MASK)
            + (partial03 & INT_MASK)
            + (i2 >>> Integer.SIZE);
    long i4 =
        (partial40 & INT_MASK)
            + (partial31 & INT_MASK)
            + (partial22 & INT_MASK)
            + (partial13 & INT_MASK)
            + (partial04 & INT_MASK)
            + (i3 >>> Integer.SIZE);
    long i5 =
        (partial50 & INT_MASK)
            + (partial41 & INT_MASK)
            + (partial32 & INT_MASK)
            + (partial23 & INT_MASK)
            + (partial14 & INT_MASK)
            + (partial05 & INT_MASK)
            + (i4 >>> Integer.SIZE);
    long i6 =
        (partial60 & INT_MASK)
            + (partial51 & INT_MASK)
            + (partial42 & INT_MASK)
            + (partial33 & INT_MASK)
            + (partial24 & INT_MASK)
            + (partial15 & INT_MASK)
            + (partial06 & INT_MASK)
            + (i5 >>> Integer.SIZE);
    long i7 =
        (partial70 & INT_MASK)
            + (partial61 & INT_MASK)
            + (partial52 & INT_MASK)
            + (partial43 & INT_MASK)
            + (partial34 & INT_MASK)
            + (partial25 & INT_MASK)
            + (partial16 & INT_MASK)
            + (partial07 & INT_MASK)
            + (i6 >>> Integer.SIZE);

    return UInt256.from(
        UInt128.from((int) i7, (int) i6, (int) i5, (int) i4),
        UInt128.from((int) i3, (int) i2, (int) i1, (int) i0));
  }

  /**
   * Divides {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The result {@code floor(this / divisor)}.
   * @throws IllegalArgumentException if {@code divisor} is zero
   */
  public UInt256 divide(UInt256 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    BigInteger q = new BigInteger(1, this.toByteArray());
    BigInteger d = new BigInteger(1, divisor.toByteArray());
    return new UInt256(q.divide(d).toByteArray());
  }

  /**
   * Returns the remainder of the division of {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The remainder of the division {@code this / divisor}.
   */
  public UInt256 remainder(UInt256 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    BigInteger q = new BigInteger(1, this.toByteArray());
    BigInteger d = new BigInteger(1, divisor.toByteArray());
    return new UInt256(q.remainder(d).toByteArray());
  }

  /**
   * Calculates {@code this}<sup>{@code exp}</sup>.
   *
   * @param exp the exponent to raise {@code this} to
   * @return {@code this}<sup>{@code exp}</sup>
   */
  public UInt256 pow(int exp) {
    if (exp < 0) {
      throw new IllegalArgumentException("exp must be >= 0");
    }

    // Mirrors algorithm in multiply(...)
    UInt256 result = UInt256.ONE;
    UInt256 base = this;

    while (exp != 0) {
      if ((exp & 1) != 0) {
        result = result.multiply(base);
      }

      base = base.multiply(base);
      exp >>>= 1;
    }
    return result;
  }

  /**
   * Calculates the integer square root, that is the largest number {@code n} such that {@code n * n
   * <= this}.
   *
   * @return The integer square root.
   */
  public UInt256 isqrt() {
    UInt256 bit = UInt256.ONE.shiftLeft(SIZE - 2);

    // "bit" starts at the highest power of four <= this
    UInt256 num = this;
    while (bit.compareTo(num) > 0) {
      bit = bit.shiftRight(2);
    }

    UInt256 res = UInt256.ZERO;
    while (!bit.isZero()) {
      UInt256 rab = res.add(bit);
      if (num.compareTo(rab) >= 0) {
        num = num.subtract(rab);
        res = res.shiftRight().add(bit);
      } else {
        res = res.shiftRight();
      }
      bit = bit.shiftRight(2);
    }
    return res;
  }

  /**
   * Shifts {@code this} left 1 bit. A zero bit is moved into the rightmost bit.
   *
   * @return The result of shifting {@code this} left one bit.
   */
  public UInt256 shiftLeft() {
    UInt128 h = this.high.shiftLeft();
    if (this.low.isHighBitSet()) {
      h = h.or(UInt128.ONE);
    }
    UInt128 l = this.low.shiftLeft();
    return UInt256.from(h, l);
  }

  /**
   * Shift {@code this} left n bits. A zero bit is moved into the rightmost bits.
   *
   * @return The result of shifting {@code this} left n bits.
   */
  public UInt256 shiftLeft(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftRight(-n); // -ve left shift is right shift
    }
    UInt128 h = (n >= UInt128.SIZE) ? this.low : this.high;
    UInt128 l = (n >= UInt128.SIZE) ? UInt128.ZERO : this.low;
    int r = n % UInt128.SIZE;
    if (r > 0) {
      UInt128 c = l.shiftRight(UInt128.SIZE - r);
      h = h.shiftLeft(r).or(c);
      l = l.shiftLeft(r);
    }
    return UInt256.from(h, l);
  }

  /**
   * Shifts {@code this} right 1 bit. A zero bit is moved into the into the leftmost bit.
   *
   * @return The result of arithmetic shifting {@code this} right one bit.
   */
  public UInt256 shiftRight() {
    UInt128 h = this.high.shiftRight();
    UInt128 l = this.low.shiftRight();
    if (this.high.isOdd()) {
      l = l.or(UInt128.HIGH_BIT);
    }
    return UInt256.from(h, l);
  }

  /**
   * Logical shift {@code this} right n bits. Zeros are shifted into the leftmost bits.
   *
   * @return The result of logical shifting {@code this} right n bits.
   */
  public UInt256 shiftRight(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftLeft(-n); // -ve right shift is left shift
    }
    UInt128 h = (n >= UInt128.SIZE) ? UInt128.ZERO : this.high;
    UInt128 l = (n >= UInt128.SIZE) ? this.high : this.low;
    int r = n % UInt128.SIZE;
    if (r > 0) {
      UInt128 c = h.shiftLeft(UInt128.SIZE - r);
      h = h.shiftRight(r);
      l = l.shiftRight(r).or(c);
    }
    return UInt256.from(h, l);
  }

  /**
   * Returns the value of {@code ~this}.
   *
   * @return The logical inverse of {@code this}.
   */
  public UInt256 invert() {
    return UInt256.from(this.high.invert(), this.low.invert());
  }

  @Override
  public int compareTo(UInt256 n) {
    int cmp = this.high.compareTo(n.high);
    if (cmp == 0) {
      cmp = this.low.compareTo(n.low);
    }
    return cmp;
  }

  /**
   * Returns the most significant word.
   *
   * @return the most significant word.
   */
  public UInt128 getHigh() {
    return this.high;
  }

  /**
   * Returns the least significant word.
   *
   * @return the least significant word.
   */
  public UInt128 getLow() {
    return this.low;
  }

  /**
   * Calculates the bitwise inclusive-or of {@code this} with {@code other} ({@code this | other}).
   *
   * @param other The value to inclusive-or with {@code this}.
   * @return {@code this | other}
   */
  public UInt256 or(UInt256 other) {
    return UInt256.from(this.high.or(other.high), this.low.or(other.low));
  }

  /**
   * Calculates the bitwise and of {@code this} with {@code other} ({@code this & other}).
   *
   * @param other The value to and with {@code this}.
   * @return {@code this & other}
   */
  public UInt256 and(UInt256 other) {
    return UInt256.from(this.high.and(other.high), this.low.and(other.low));
  }

  /**
   * Calculates the exclusive-or of {@code this} with {@code other} ({@code this ^ other}).
   *
   * @param other The value to exclusive-or with {@code this}.
   * @return {@code this ^ other}
   */
  public UInt256 xor(UInt256 other) {
    return UInt256.from(this.high.xor(other.high), this.low.xor(other.low));
  }

  /**
   * Returns the number of zero bits preceding the highest-order ("leftmost") one-bit in the two's
   * complement binary representation of the specified {@code long} value. Returns 128 if the
   * specified value has no one-bits in its two's complement representation, in other words if it is
   * equal to zero.
   *
   * <p>Note that this method is closely related to the logarithm base 2. For all positive {@code
   * long} values x:
   *
   * <ul>
   *   <li>floor(log<sub>2</sub>(x)) = {@code 255 - numberOfLeadingZeros(x)}
   *   <li>ceil(log<sub>2</sub>(x)) = {@code 256 - numberOfLeadingZeros(x - 1)}
   * </ul>
   *
   * @return the number of zero bits preceding the highest-order ("leftmost") one-bit in the two's
   *     complement binary representation of the specified {@code long} value, or 256 if the value
   *     is equal to zero.
   */
  public int numberOfLeadingZeros() {
    return this.high.isZero()
        ? UInt128.SIZE + this.low.numberOfLeadingZeros()
        : this.high.numberOfLeadingZeros();
  }

  /**
   * Return {@code true} if the {@link UInt128} has its high bit set.
   *
   * @return {@code true} if the {@link UInt128} has its high bit set.
   */
  public boolean isHighBitSet() {
    return this.high.isHighBitSet();
  }

  /**
   * Returns {@code true} if {@code this} is zero.
   *
   * @return {@code true} if {@code this} is zero.
   */
  public boolean isZero() {
    return this.high.isZero() && this.low.isZero();
  }

  /**
   * Returns {@code true} if {@code this} is an even number.
   *
   * @return {@code true} if {@code this} is an even number.
   */
  public boolean isEven() {
    return (this.low.low & 1) == 0;
  }

  /**
   * Returns {@code true} if {@code this} is an odd number.
   *
   * @return {@code true} if {@code this} is an odd number.
   */
  public boolean isOdd() {
    return (this.low.low & 1) != 0;
  }

  @Override
  public int hashCode() {
    return this.high.hashCode() * 31 + this.low.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    // Note that this needs to be consistent with compareTo
    if (this == obj) {
      return true;
    }
    if (obj instanceof UInt256) {
      UInt256 other = (UInt256) obj;
      return Objects.equals(this.high, other.high) && Objects.equals(this.low, other.low);
    }
    return false;
  }

  @JsonValue
  public String toJson() {
    return toString(10);
  }

  @Override
  public String toString() {
    return toString(10);
  }

  /**
   * Returns a string representation of this object in the specified radix.
   *
   * <p>If the radix is smaller than {@code Character.MIN_RADIX} or larger than {@code
   * Character.MAX_RADIX}, an {@link IllegalArgumentException} is thrown.
   *
   * <p>The characters of the result represent the magnitude of {@code this}. If the magnitude is
   * zero, it is represented by a single zero character {@code '0'}; otherwise no leading zeros are
   * output.
   *
   * <p>The following ASCII characters are used as digits:
   *
   * <blockquote>
   *
   * {@code 0123456789abcdefghijklmnopqrstuvwxyz}
   *
   * </blockquote>
   *
   * <p>If {@code radix} is <var>N</var>, then the first <var>N</var> of these characters are used
   * as radix-<var>N</var> digits in the order shown, i.e. the digits for hexadecimal (radix 16) are
   * {@code 0123456789abcdef}.
   *
   * @param radix the radix to use in the string representation.
   * @return a string representation of the argument in the specified radix.
   * @throws IllegalArgumentException if {@code radix} is less than {@code Character.MIN_RADIX} or
   *     greater than {@code Character.MAX_RADIX}.
   * @see Character#MAX_RADIX
   * @see Character#MIN_RADIX
   */
  public String toString(int radix) {
    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
      throw new IllegalArgumentException("Illegal radix: " + radix);
    }
    if (isZero()) {
      return "0";
    }
    StringBuilder sb = new StringBuilder();
    UInt256 n = this;
    UInt256 r = UInt256.from(radix);
    while (!n.isZero()) {
      UInt256 digit = n.remainder(r);
      sb.append(Character.forDigit(digit.low.low, radix));
      n = n.divide(r);
    }
    return sb.reverse().toString();
  }

  public BigInteger toBigInt() {
    return new BigInteger(1, this.toByteArray());
  }
}
