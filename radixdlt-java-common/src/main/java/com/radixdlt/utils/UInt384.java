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

import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.utils.functional.Result;
import java.util.Arrays;
import java.util.Objects;

/** A 384-bit unsigned integer, with comparison and some basic arithmetic operations. */
@SecurityCritical(SecurityKind.NUMERIC)
public final class UInt384 implements Comparable<UInt384> {
  // Some sizing constants in line with Integer, Long etc
  /** Size of this numeric type in bits. */
  public static final int SIZE = UInt128.SIZE + UInt256.SIZE;

  /** Size of this numeric type in bytes. */
  public static final int BYTES = UInt128.BYTES + UInt256.BYTES;

  /** A constant holding the minimum value a {@code UInt384} can have, 0. */
  public static final UInt384 MIN_VALUE = new UInt384(UInt128.ZERO, UInt256.ZERO);

  /** Highest bit. */
  public static final UInt384 HIGH_BIT = new UInt384(UInt128.HIGH_BIT, UInt256.ZERO);

  /** A constant holding the maximum value an {@code Int256} can have, 2<sup>384</sup>-1. */
  public static final UInt384 MAX_VALUE = new UInt384(UInt128.MAX_VALUE, UInt256.MAX_VALUE);

  // Some commonly used values
  public static final UInt384 ZERO = new UInt384(UInt128.ZERO, UInt256.ZERO);
  public static final UInt384 ONE = new UInt384(UInt128.ZERO, UInt256.ONE);
  public static final UInt384 TWO = new UInt384(UInt128.ZERO, UInt256.TWO);
  public static final UInt384 THREE = new UInt384(UInt128.ZERO, UInt256.THREE);
  public static final UInt384 FOUR = new UInt384(UInt128.ZERO, UInt256.FOUR);
  public static final UInt384 FIVE = new UInt384(UInt128.ZERO, UInt256.FIVE);
  public static final UInt384 SIX = new UInt384(UInt128.ZERO, UInt256.SIX);
  public static final UInt384 SEVEN = new UInt384(UInt128.ZERO, UInt256.SEVEN);
  public static final UInt384 EIGHT = new UInt384(UInt128.ZERO, UInt256.EIGHT);
  public static final UInt384 NINE = new UInt384(UInt128.ZERO, UInt256.NINE);
  public static final UInt384 TEN = new UInt384(UInt128.ZERO, UInt256.TEN);

  // Numbers in order.  This is used by factory methods.
  private static final UInt384[] numbers = {
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
  };

  // The actual value.
  // @PackageLocalForTest
  final UInt128 high;
  // @PackageLocalForTest
  final UInt256 low;

  /**
   * Factory method for materialising an {@link UInt384} from a {@code short} value.
   *
   * @param value The value to be represented as an {@link UInt384}.
   * @return {@code value} as an {@link UInt384} type.
   */
  public static UInt384 from(short value) {
    return from((long) value);
  }

  /**
   * Factory method for materialising an {@link UInt384} from an {@code int} value.
   *
   * @param value The value to be represented as an {@link UInt384}.
   * @return {@code value} as an {@link UInt384} type.
   */
  public static UInt384 from(int value) {
    return from((long) value);
  }

  /**
   * Factory method for materialising an {@link UInt384} from a {@code long} value. Note that values
   * are zero extended into the 256 bit value.
   *
   * @param value The value to be represented as an {@link UInt384}.
   * @return {@code value} as an {@link UInt384} type.
   */
  public static UInt384 from(long value) {
    return from(UInt128.from(value));
  }

  /**
   * Factory method for materialising an {@link UInt384} from an {@link UInt128} value. Note that
   * values are zero extended into the 256 bit value.
   *
   * @param value The least significant half-word of the value.
   * @return the specified value as an {@link UInt384} type.
   */
  public static UInt384 from(UInt128 value) {
    return from(UInt256.from(value));
  }

  /**
   * Factory method for materialising an {@link UInt384} from an {@link UInt128} value. Note that
   * values are zero extended into the 256 bit value.
   *
   * @param value The least significant word of the value.
   * @return the specified value as an {@link UInt384} type.
   */
  public static UInt384 from(UInt256 value) {
    return from(UInt128.ZERO, value);
  }

  /**
   * Factory method for materialising an {@link UInt384} from constituent {@link UInt128} and {@link
   * UInt256} parts.
   *
   * @param high The most significant half-word of the value.
   * @param low The least significant word of the value.
   * @return the specified values as an {@link UInt384} type.
   */
  public static UInt384 from(UInt128 high, UInt256 low) {
    return new UInt384(high, low);
  }

  /**
   * Factory method for materialising an {@link UInt384} from an array of bytes. The array is
   * most-significant byte first, and must not be zero length.
   *
   * <p>If the array is smaller than {@link #BYTES}, then it is effectively padded with leading zero
   * bytes.
   *
   * <p>If the array is longer than {@link #BYTES}, then values at index {@link #BYTES} and beyond
   * are ignored.
   *
   * @param bytes The array of bytes to be used.
   * @return {@code bytes} as an {@link UInt384} type.
   * @throws IllegalArgumentException if {@code bytes} is 0 length.
   * @see #toByteArray()
   */
  public static UInt384 from(byte[] bytes) {
    Objects.requireNonNull(bytes);
    if (bytes.length == 0) {
      throw new IllegalArgumentException("bytes is 0 bytes long");
    }
    byte[] newBytes = extend(bytes);
    return from(newBytes, 0);
  }

  /**
   * Factory method for materialising an {@link UInt384} from an array of bytes. The array is
   * most-significant byte first.
   *
   * @param bytes The array of bytes to be used.
   * @param offset The offset within the array to be used.
   * @return {@code bytes} from {@code offset} as an {@link UInt384} type.
   * @see #toByteArray()
   */
  public static UInt384 from(byte[] bytes, int offset) {
    UInt128 high = UInt128.from(bytes, offset);
    UInt256 low = UInt256.from(bytes, offset + UInt128.BYTES);
    return from(high, low);
  }

  /**
   * Factory method for materialising an {@link UInt384} from a string. Conversion is performed base
   * 10 and leading '+' sign character is permitted.
   *
   * @param s The array of bytes to be used.
   * @return {@code s} as an {@link UInt384} type.
   * @throws NumberFormatException if {@code s} is not a valid integer number.
   */
  public static UInt384 from(String s) {
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
      UInt384 result = UInt384.ZERO;
      while (i < len) {
        int digit = Character.digit(s.charAt(i++), 10);
        if (digit < 0) {
          throw new NumberFormatException(s);
        }
        result = result.multiply(UInt384.TEN).add(numbers[digit]);
      }
      return result;
    } else {
      throw new NumberFormatException(s);
    }
  }

  /**
   * Functional style friendly version of {@link #from(String)}.
   *
   * @param input The string to parse
   * @return Success {@link Result} if value can be successfully parsed and failure {@link Result}
   *     otherwise.
   */
  public static Result<UInt384> fromString(String input) {
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

  private UInt384(UInt128 high, UInt256 low) {
    this.high = Objects.requireNonNull(high);
    this.low = Objects.requireNonNull(low);
  }

  /**
   * Converts {@code this} to an array of bytes. The most significant byte will be returned in index
   * zero. The array will always be {@link #BYTES} bytes long, and will be zero filled to suit the
   * actual value.
   *
   * @return An array of {@link #BYTES} bytes representing the value of this {@link UInt384}.
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
   * @return An {@link UInt384} with the value {@code this + other}.
   */
  public UInt384 add(UInt384 other) {
    UInt256 newLow = this.low.add(other.low);
    // Hacker's Delight section 2-13:
    // "The following branch-free code can be used to compute the
    // overflow predicate for unsigned add/subtract, with the result
    // being in the sign position."
    // Note that the use of method calls and the ternary operator
    // very likely precludes this from being branch-free in java.
    UInt128 carry =
        this.low
                .shiftRight()
                .add(other.low.shiftRight())
                .add(this.low.and(other.low).and(UInt256.ONE))
                .isHighBitSet()
            ? UInt128.ONE
            : UInt128.ZERO;
    UInt128 newHigh = this.high.add(other.high).add(carry);
    return UInt384.from(newHigh, newLow);
  }

  /**
   * Adds {@code other} to {@code this}, returning the result.
   *
   * @param other The addend.
   * @return An {@link UInt384} with the value {@code this + other}.
   */
  public UInt384 add(UInt256 other) {
    UInt256 newLow = this.low.add(other);
    // Hacker's Delight section 2-13:
    // "The following branch-free code can be used to compute the
    // overflow predicate for unsigned add/subtract, with the result
    // being in the sign position."
    // Note that the use of method calls and the ternary operator
    // very likely precludes this from being branch-free in java.
    UInt128 newHigh =
        this.low
                .shiftRight()
                .add(other.low.shiftRight())
                .add(this.low.and(other).and(UInt256.ONE))
                .isHighBitSet()
            ? this.high.increment()
            : this.high;
    return UInt384.from(newHigh, newLow);
  }

  /**
   * Subtracts {@code other} from {@code this}, returning the result.
   *
   * @param other The subtrahend.
   * @return An {@link UInt384} with the value {@code this - other}.
   */
  public UInt384 subtract(UInt384 other) {
    UInt256 newLow = this.low.subtract(other.low);
    // Hacker's Delight section 2-13:
    // "The following branch-free code can be used to compute the
    // overflow predicate for unsigned add/subtract, with the result
    // being in the sign position."
    // Note that the use of method calls and the ternary operator
    // very likely precludes this from being branch-free in java.
    UInt128 carry =
        this.low
                .shiftRight()
                .subtract(other.low.shiftRight())
                .subtract(this.low.invert().and(other.low).and(UInt256.ONE))
                .isHighBitSet()
            ? UInt128.ONE
            : UInt128.ZERO;
    UInt128 newHigh = this.high.subtract(other.high).subtract(carry);
    return UInt384.from(newHigh, newLow);
  }

  /**
   * Subtracts {@code other} from {@code this}, returning the result.
   *
   * @param other The subtrahend.
   * @return An {@link UInt384} with the value {@code this - other}.
   */
  public UInt384 subtract(UInt256 other) {
    UInt256 newLow = this.low.subtract(other);
    // Hacker's Delight section 2-13:
    // "The following branch-free code can be used to compute the
    // overflow predicate for unsigned add/subtract, with the result
    // being in the sign position."
    // Note that the use of method calls and the ternary operator
    // very likely precludes this from being branch-free in java.
    UInt128 newHigh =
        this.low
                .shiftRight()
                .subtract(other.low.shiftRight())
                .subtract(this.low.invert().and(other).and(UInt256.ONE))
                .isHighBitSet()
            ? this.high.decrement()
            : this.high;
    return UInt384.from(newHigh, newLow);
  }

  /**
   * Increments {@code this}. Equivalent to {@code this.add(Int256.ONE)}, but faster.
   *
   * @return This number incremented by one.
   */
  public UInt384 increment() {
    UInt256 l = this.low.increment();
    UInt128 h = l.isZero() ? this.high.increment() : this.high;
    return UInt384.from(h, l);
  }

  /**
   * Decrements {@code this}. Equivalent to {@code this.subtract(Int256.ONE)}, but faster.
   *
   * @return This number decremented by one.
   */
  public UInt384 decrement() {
    UInt256 l = this.low.decrement();
    UInt128 h = this.low.isZero() ? this.high.decrement() : this.high;
    return UInt384.from(h, l);
  }

  /**
   * Multiplies {@code this} by the specified multiplicand.
   *
   * @param multiplicand The multiplicand to multiply {@code this} by.
   * @return The result {@code this * multiplicand}.
   */
  public UInt384 multiply(UInt384 multiplicand) {
    // Russian peasant
    UInt384 result = UInt384.ZERO;
    UInt384 multiplier = this;

    while (!multiplicand.isZero()) {
      if (multiplicand.isOdd()) {
        result = result.add(multiplier);
      }

      multiplier = multiplier.shiftLeft();
      multiplicand = multiplicand.shiftRight();
    }
    return result;
  }

  /**
   * Multiplies {@code this} by the specified multiplicand.
   *
   * @param multiplicand The multiplicand to multiply {@code this} by.
   * @return The result {@code this * multiplicand}.
   */
  public UInt384 multiply(UInt256 multiplicand) {
    // Russian peasant
    UInt384 result = UInt384.ZERO;
    UInt384 multiplier = this;

    while (!multiplicand.isZero()) {
      if (multiplicand.isOdd()) {
        result = result.add(multiplier);
      }

      multiplier = multiplier.shiftLeft();
      multiplicand = multiplicand.shiftRight();
    }
    return result;
  }

  /**
   * Divides {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The result {@code floor(this / divisor)}.
   * @throws IllegalArgumentException if {@code divisor} is zero
   */
  public UInt384 divide(UInt384 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    UInt384 q = UInt384.ZERO;
    UInt384 r = UInt384.ZERO;
    UInt384 n = this;
    for (int i = 0; i < SIZE; ++i) {
      r = r.shiftLeft();
      q = q.shiftLeft();
      if (n.high.isHighBitSet()) {
        r = r.or(UInt384.ONE);
      }
      n = n.shiftLeft();
      if (r.compareTo(divisor) >= 0) {
        r = r.subtract(divisor);
        q = q.or(UInt384.ONE);
      }
    }
    return q;
  }

  /**
   * Divides {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The result {@code floor(this / divisor)}.
   * @throws IllegalArgumentException if {@code divisor} is zero
   */
  public UInt384 divide(UInt256 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    UInt384 q = UInt384.ZERO;
    UInt384 r = UInt384.ZERO;
    UInt384 n = this;
    for (int i = 0; i < SIZE; ++i) {
      r = r.shiftLeft();
      q = q.shiftLeft();
      if (n.high.isHighBitSet()) {
        r = r.or(UInt384.ONE);
      }
      n = n.shiftLeft();
      if (!r.high.isZero() || (r.low.compareTo(divisor) >= 0)) {
        r = r.subtract(divisor);
        q = q.or(UInt384.ONE);
      }
    }
    return q;
  }

  /**
   * Returns the remainder of the division of {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The remainder of the division {@code this / divisor}.
   */
  public UInt384 remainder(UInt384 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    UInt384 r = UInt384.ZERO;
    UInt384 n = this;
    for (int i = 0; i < SIZE; ++i) {
      r = r.shiftLeft();
      if (n.high.isHighBitSet()) {
        r = r.or(UInt384.ONE);
      }
      n = n.shiftLeft();
      if (r.compareTo(divisor) >= 0) {
        r = r.subtract(divisor);
      }
    }
    return r;
  }

  /**
   * Calculates {@code this}<sup>{@code exp}</sup>.
   *
   * @param exp the exponent to raise {@code this} to
   * @return {@code this}<sup>{@code exp}</sup>
   */
  public UInt384 pow(int exp) {
    if (exp < 0) {
      throw new IllegalArgumentException("exp must be >= 0");
    }

    // Mirrors algorithm in multiply(...)
    UInt384 result = UInt384.ONE;
    UInt384 base = this;

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
  public UInt384 isqrt() {
    UInt384 bit = UInt384.ONE.shiftLeft(SIZE - 2);

    // "bit" starts at the highest power of four <= this
    UInt384 num = this;
    while (bit.compareTo(num) > 0) {
      bit = bit.shiftRight(2);
    }

    UInt384 res = UInt384.ZERO;
    while (!bit.isZero()) {
      UInt384 rab = res.add(bit);
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
  public UInt384 shiftLeft() {
    UInt128 h = this.high.shiftLeft();
    if (this.low.isHighBitSet()) {
      h = h.or(UInt128.ONE);
    }
    UInt256 l = this.low.shiftLeft();
    return UInt384.from(h, l);
  }

  /**
   * Shift {@code this} left n bits. A zero bit is moved into the rightmost bits.
   *
   * @return The result of shifting {@code this} left n bits.
   */
  public UInt384 shiftLeft(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftRight(-n); // -ve left shift is right shift
    }
    UInt256 h = (n >= UInt256.SIZE) ? this.low : UInt256.from(this.high);
    UInt256 l = (n >= UInt256.SIZE) ? UInt256.ZERO : this.low;
    int r = n % UInt256.SIZE;
    if (r > 0) {
      UInt256 c = l.shiftRight(UInt256.SIZE - r);
      h = h.shiftLeft(r).or(c);
      l = l.shiftLeft(r);
    }
    return UInt384.from(h.low, l);
  }

  /**
   * Shifts {@code this} right 1 bit. A zero bit is moved into the into the left bit.
   *
   * @return The result of arithmetic shifting {@code this} right one bit.
   */
  public UInt384 shiftRight() {
    UInt128 h = this.high.shiftRight();
    UInt256 l = this.low.shiftRight();
    if (this.high.isOdd()) {
      l = l.or(UInt256.HIGH_BIT);
    }
    return UInt384.from(h, l);
  }

  /**
   * Logical shift {@code this} right n bits. Zeros are shifted into the leftmost bits.
   *
   * @return The result of logical shifting {@code this} right n bits.
   */
  public UInt384 shiftRight(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftLeft(-n); // -ve right shift is left shift
    }
    UInt256 h = (n >= UInt256.SIZE) ? UInt256.ZERO : UInt256.from(this.high);
    UInt256 l = (n >= UInt256.SIZE) ? UInt256.from(this.high) : this.low;
    int r = n % UInt256.SIZE;
    if (r > 0) {
      UInt256 c = h.shiftLeft(UInt256.SIZE - r);
      h = h.shiftRight(r);
      l = l.shiftRight(r).or(c);
    }
    return UInt384.from(h.low, l);
  }

  /**
   * Returns the value of {@code ~this}.
   *
   * @return The logical inverse of {@code this}.
   */
  public UInt384 invert() {
    return UInt384.from(this.high.invert(), this.low.invert());
  }

  @Override
  public int compareTo(UInt384 n) {
    int cmp = this.high.compareTo(n.high);
    if (cmp == 0) {
      cmp = this.low.compareTo(n.low);
    }
    return cmp;
  }

  /**
   * Returns the most significant half-word.
   *
   * @return the most significant half-word.
   */
  public UInt128 getHigh() {
    return this.high;
  }

  /**
   * Returns the least significant word.
   *
   * @return the least significant word.
   */
  public UInt256 getLow() {
    return this.low;
  }

  /**
   * Calculates the bitwise inclusive-or of {@code this} with {@code other} ({@code this | other}).
   *
   * @param other The value to inclusive-or with {@code this}.
   * @return {@code this | other}
   */
  public UInt384 or(UInt384 other) {
    return UInt384.from(this.high.or(other.high), this.low.or(other.low));
  }

  /**
   * Calculates the bitwise and of {@code this} with {@code other} ({@code this & other}).
   *
   * @param other The value to and with {@code this}.
   * @return {@code this & other}
   */
  public UInt384 and(UInt384 other) {
    return UInt384.from(this.high.and(other.high), this.low.and(other.low));
  }

  /**
   * Calculates the exclusive-or of {@code this} with {@code other} ({@code this ^ other}).
   *
   * @param other The value to exclusive-or with {@code this}.
   * @return {@code this ^ other}
   */
  public UInt384 xor(UInt384 other) {
    return UInt384.from(this.high.xor(other.high), this.low.xor(other.low));
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
    return this.low.isEven();
  }

  /**
   * Returns {@code true} if {@code this} is an odd number.
   *
   * @return {@code true} if {@code this} is an odd number.
   */
  public boolean isOdd() {
    return this.low.isOdd();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.high) * 31 + Objects.hashCode(this.low);
  }

  @Override
  public boolean equals(Object obj) {
    // Note that this needs to be consistent with compareTo
    if (this == obj) {
      return true;
    }

    if (obj instanceof UInt384) {
      UInt384 other = (UInt384) obj;
      return Objects.equals(this.high, other.high) && Objects.equals(this.low, other.low);
    }
    return false;
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
   * If {@code radix} is <var>N</var>, then the first <var>N</var> of these characters are used as
   * radix-<var>N</var> digits in the order shown, i.e. the digits for hexadecimal (radix 16) are
   * {@code 0123456789abcdef}.
   *
   * @param radix the radix to use in the string representation.
   * @return a string representation of the argument in the specified radix.
   * @see Character#MAX_RADIX
   * @throws IllegalArgumentException if {@code radix} is less than {@code Character.MIN_RADIX} or
   *     greater than {@code Character.MAX_RADIX}.
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
    UInt384 n = this;
    UInt384 r = UInt384.from(radix);
    while (!n.isZero()) {
      UInt384 digit = n.remainder(r);
      sb.append(Character.forDigit(digit.low.low.low, radix));
      n = n.divide(r);
    }
    return sb.reverse().toString();
  }
}
