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
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/** A 128-bit unsigned integer, with comparison and some basic arithmetic operations. */
@SecurityCritical(SecurityKind.NUMERIC)
public final class UInt128 implements Comparable<UInt128>, Serializable {
  // Some sizing constants in line with Integer, Long etc
  /** Size of this numeric type in bits. */
  public static final int SIZE = Integer.SIZE * 4;
  /** Size of this numeric type in bytes. */
  public static final int BYTES = Integer.BYTES * 4;

  /** A constant holding the minimum value an {@code UInt128} can have, 0. */
  public static final UInt128 MIN_VALUE =
      new UInt128(0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L);

  /** A constant holding the maximum value an {@code UInt128} can have, 2<sup>128</sup>-1. */
  public static final UInt128 MAX_VALUE =
      new UInt128(0xFFFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL);

  // Some commonly used values
  public static final UInt128 ZERO = new UInt128(0L, 0L);
  public static final UInt128 ONE = new UInt128(0L, 1L);
  public static final UInt128 TWO = new UInt128(0L, 2L);
  public static final UInt128 THREE = new UInt128(0L, 3L);
  public static final UInt128 FOUR = new UInt128(0L, 4L);
  public static final UInt128 FIVE = new UInt128(0L, 5L);
  public static final UInt128 SIX = new UInt128(0L, 6L);
  public static final UInt128 SEVEN = new UInt128(0L, 7L);
  public static final UInt128 EIGHT = new UInt128(0L, 8L);
  public static final UInt128 NINE = new UInt128(0L, 9L);
  public static final UInt128 TEN = new UInt128(0L, 10L);

  /** Number with the highest bit set. Value is equal to 2<sup>127</sup>. */
  public static final UInt128 HIGH_BIT = new UInt128(0x8000_0000_0000_0000L, 0);

  // Numbers in order.  This is used by factory methods.
  private static final UInt128[] numbers = {
    ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
  };

  // Mask of int bits as a long
  private static final long INT_MASK = (1L << Integer.SIZE) - 1L;

  // The actual value.
  // We use integers here so that we can use double-wide long multiplication.
  // This significantly improves performance of the multiply operation.
  final int high;
  final int midHigh;
  final int midLow;
  final int low;

  /**
   * Factory method for materialising an {@link UInt128} from a {@code short} value.
   *
   * @param value The value to be represented as an {@link UInt128}.
   * @return {@code value} as an {@link UInt128} type.
   */
  public static UInt128 from(short value) {
    return from(value & 0xFFFF);
  }

  /**
   * Factory method for materialising an {@link UInt128} from an {@code int} value.
   *
   * @param value The value to be represented as an {@link UInt128}.
   * @return {@code value} as an {@link UInt128} type.
   */
  public static UInt128 from(int value) {
    return from(value & 0xFFFF_FFFFL);
  }

  /**
   * Factory method for materialising an {@link UInt128} from a {@code long} value. Note that values
   * are zero extended into the 128 bit value.
   *
   * @param value The value to be represented as an {@link UInt128}.
   * @return {@code value} as an {@link UInt128} type.
   */
  public static UInt128 from(long value) {
    // Assume unsigned long
    return from(0L, value);
  }

  /**
   * Factory method for materialising an {@link UInt128} from two {@code long} values. {@code high}
   * is the most significant word, and {@code low} the least significant.
   *
   * @param high The most significant word of the 128 bit value.
   * @param low The least significant word of the 128 bit value.
   * @return {@code (high << 64) | low} as an {@link UInt128} type.
   */
  public static UInt128 from(long high, long low) {
    return from((int) (high >>> Integer.SIZE), (int) high, (int) (low >>> Integer.SIZE), (int) low);
  }

  /**
   * Factory method for materialising an {@link UInt128} from four {@code int} values. {@code high}
   * is the most significant word, and {@code low} the least significant.
   *
   * @param high The most significant word of the 128 bit value.
   * @param low The least significant word of the 128 bit value.
   * @return {@code (high << 64) | low} as an {@link UInt128} type.
   */
  public static UInt128 from(int high, int midHigh, int midLow, int low) {
    if (high == 0 && midHigh == 0 && midLow == 0) {
      if (low >= 0L && low < numbers.length) {
        return numbers[low];
      }
    }
    return new UInt128(high, midHigh, midLow, low);
  }

  /**
   * Factory method for materialising an {@link UInt128} from an array of bytes. The array is
   * most-significant byte first, and must not be zero length.
   *
   * <p>If the array is smaller than {@link #BYTES}, then it is effectively padded with leading zero
   * bytes.
   *
   * <p>If the array is longer than {@link #BYTES}, then values at index {@link #BYTES} and beyond
   * are ignored.
   *
   * @param bytes The array of bytes to be used.
   * @return {@code bytes} as an {@link UInt128} type.
   * @throws IllegalArgumentException if {@code bytes} is 0 length.
   * @see #toByteArray()
   */
  public static UInt128 from(byte[] bytes) {
    Objects.requireNonNull(bytes);
    if (bytes.length == 0) {
      throw new IllegalArgumentException("bytes is 0 bytes long");
    }
    byte[] newBytes = extend(bytes);
    return from(newBytes, 0);
  }

  /**
   * Factory method for materialising an {@link UInt128} from an array of bytes. The array is
   * most-significant byte first.
   *
   * @param bytes The array of bytes to be used.
   * @param offset The offset within the array to be used.
   * @return {@code bytes} from {@code offset} as an {@link UInt128} type.
   * @see #toByteArray()
   */
  public static UInt128 from(byte[] bytes, int offset) {
    long high = Longs.fromByteArray(bytes, offset);
    long low = Longs.fromByteArray(bytes, offset + Long.BYTES);
    return from(high, low);
  }

  /**
   * Factory method for materialising an {@link UInt128} from a string. Conversion is performed base
   * 10 and leading '+' sign character is permitted.
   *
   * @param s The array of bytes to be used.
   * @return {@code s} as an {@link UInt128} type.
   * @throws NumberFormatException if {@code s} is not a valid integer number.
   */
  public static UInt128 from(String s) {
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
      UInt128 result = UInt128.ZERO;
      while (i < len) {
        int digit = Character.digit(s.charAt(i++), 10);
        if (digit < 0) {
          throw new NumberFormatException(s);
        }
        result = result.multiply(UInt128.TEN).add(numbers[digit]);
      }
      return result;
    } else {
      throw new NumberFormatException(s);
    }
  }

  // Pad short (< BYTES length) array with appropriate lead bytes.
  private static byte[] extend(byte[] bytes) {
    if (bytes.length >= BYTES) {
      return bytes;
    }
    byte[] newBytes = new byte[BYTES];
    int newPos = BYTES - bytes.length;
    // Zero extension
    Arrays.fill(newBytes, 0, newPos, (byte) 0);
    System.arraycopy(bytes, 0, newBytes, newPos, bytes.length);
    return newBytes;
  }

  private UInt128(long high, long low) {
    this((int) (high >>> Integer.SIZE), (int) high, (int) (low >>> Integer.SIZE), (int) low);
  }

  private UInt128(int high, int midHigh, int midLow, int low) {
    this.high = high;
    this.midHigh = midHigh;
    this.midLow = midLow;
    this.low = low;
  }

  /**
   * Convert value to an array of bytes. The most significant byte will be returned in index zero.
   * The array will always be {@link #BYTES} bytes long, and will be zero filled to suit the actual
   * value.
   *
   * @return An array of {@link #BYTES} bytes representing the value of this {@link UInt128}.
   */
  public byte[] toByteArray() {
    return toByteArray(new byte[BYTES], 0);
  }

  /**
   * Convert value to an array of bytes. The most significant byte will be returned in index {@code
   * offset}. The array must be at least {@code offset + BYTES} long.
   *
   * @param bytes The array to place the bytes in.
   * @param offset The offset within the array to place the bytes.
   * @return The passed-in value of {@code bytes}.
   */
  public byte[] toByteArray(byte[] bytes, int offset) {
    Ints.copyTo(this.high, bytes, offset);
    Ints.copyTo(this.midHigh, bytes, offset + Integer.BYTES);
    Ints.copyTo(this.midLow, bytes, offset + 2 * Integer.BYTES);
    Ints.copyTo(this.low, bytes, offset + 3 * Integer.BYTES);
    return bytes;
  }

  /**
   * Add {@code other} to {@code this}, returning the result.
   *
   * @param other The addend.
   * @return An {@link UInt128} with the value {@code this + other}.
   */
  public UInt128 add(UInt128 other) {
    long partial0 = (this.low & INT_MASK) + (other.low & INT_MASK);
    long partial1 =
        (this.midLow & INT_MASK) + (other.midLow & INT_MASK) + (partial0 >>> Integer.SIZE);
    long partial2 =
        (this.midHigh & INT_MASK) + (other.midHigh & INT_MASK) + (partial1 >>> Integer.SIZE);
    long partial3 = (this.high & INT_MASK) + (other.high & INT_MASK) + (partial2 >>> Integer.SIZE);
    return UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0);
  }

  /**
   * Subtract {@code other} from {@code this}, returning the result.
   *
   * @param other The subtrahend.
   * @return An {@link UInt128} with the value {@code this - other}.
   */
  public UInt128 subtract(UInt128 other) {
    long partial0 = (this.low & INT_MASK) - (other.low & INT_MASK);
    long partial1 =
        (this.midLow & INT_MASK) - (other.midLow & INT_MASK) + (partial0 >> Integer.SIZE);
    long partial2 =
        (this.midHigh & INT_MASK) - (other.midHigh & INT_MASK) + (partial1 >> Integer.SIZE);
    long partial3 = (this.high & INT_MASK) - (other.high & INT_MASK) + (partial2 >> Integer.SIZE);
    return UInt128.from((int) partial3, (int) partial2, (int) partial1, (int) partial0);
  }

  /**
   * Increment a number. Equivalent to {@code this.add(UInt128.ONE)}, but faster.
   *
   * @return This number incremented by one.
   */
  public UInt128 increment() {
    int l = this.low + 1;
    int ml = this.midLow;
    int mh = this.midHigh;
    int h = this.high;
    if (l == 0) {
      ml += 1;
      if (ml == 0) {
        mh += 1;
        if (mh == 0) {
          h += 1;
        }
      }
    }
    return UInt128.from(h, mh, ml, l);
  }

  /**
   * Decrement a number. Equivalent to {@code this.subtract(UInt128.ONE)}, but faster.
   *
   * @return This number decremented by one.
   */
  public UInt128 decrement() {
    int l = this.low - 1;
    int ml = this.midLow;
    int mh = this.midHigh;
    int h = this.high;
    if (l == -1) {
      ml -= 1;
      if (ml == -1) {
        mh -= 1;
        if (mh == -1) {
          h -= 1;
        }
      }
    }
    return UInt128.from(h, mh, ml, l);
  }

  /**
   * Multiply {@code this} by the specified multiplicand.
   *
   * @param multiplicand The multiplicand to multiply {@code this} by.
   * @return The result {@code this * multiplicand}.
   */
  public UInt128 multiply(UInt128 multiplicand) {
    // I appreciate that this looks like a wall of code, and it is,
    // but the underlying algorithm is long multiplication base 2^32.

    // Avoid field access ops
    long tlow = (this.low & INT_MASK);
    long tmidlow = (this.midLow & INT_MASK);
    long tmidhigh = (this.midHigh & INT_MASK);

    long llow = (multiplicand.low & INT_MASK);
    long partial00 = tlow * llow;
    long partial01 = tmidlow * llow + (partial00 >>> Integer.SIZE);
    long partial02 = tmidhigh * llow + (partial01 >>> Integer.SIZE);
    long partial03 = (this.high & INT_MASK) * llow + (partial02 >>> Integer.SIZE);

    long lmidlow = (multiplicand.midLow & INT_MASK);
    long partial10 = tlow * lmidlow;
    long partial11 = tmidlow * lmidlow + (partial10 >>> Integer.SIZE);
    long partial12 = tmidhigh * lmidlow + (partial11 >>> Integer.SIZE);

    long lmidhigh = (multiplicand.midHigh & INT_MASK);
    long partial20 = tlow * lmidhigh;
    long partial21 = tmidlow * lmidhigh + (partial20 >>> Integer.SIZE);

    long partial30 = tlow * (multiplicand.high & INT_MASK);

    long ll = (partial00 & INT_MASK);
    long ml = (partial10 & INT_MASK) + (partial01 & INT_MASK);
    long mh =
        (partial20 & INT_MASK)
            + (partial11 & INT_MASK)
            + (partial02 & INT_MASK)
            + (ml >>> Integer.SIZE);
    long hh =
        (partial30 & INT_MASK)
            + (partial21 & INT_MASK)
            + (partial12 & INT_MASK)
            + (partial03 & INT_MASK)
            + (mh >>> Integer.SIZE);

    return UInt128.from((int) hh, (int) mh, (int) ml, (int) ll);
  }

  /**
   * Divide {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The result {@code floor(this / divisor)}.
   */
  public UInt128 divide(UInt128 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    // Some special cases
    if (this.isZero()) {
      return ZERO;
    }
    int cmp = this.compareTo(divisor);
    if (cmp < 0) {
      return ZERO;
    }
    if (cmp == 0) {
      return ONE;
    }

    UInt128 q = UInt128.ZERO;
    UInt128 r = UInt128.ZERO;
    UInt128 n = this;
    for (int i = 0; i < SIZE; ++i) {
      r = r.shiftLeft();
      q = q.shiftLeft();
      if (n.high < 0) {
        r = r.or(UInt128.ONE);
      }
      n = n.shiftLeft();
      if (r.compareTo(divisor) >= 0) {
        r = r.subtract(divisor);
        q = q.or(UInt128.ONE);
      }
    }
    return q;
  }

  /**
   * Return the remainder of the division of {@code this} by the specified divisor.
   *
   * @param divisor The divisor to divide {@code this} by.
   * @return The remainder of the division {@code this / divisor}.
   */
  public UInt128 remainder(UInt128 divisor) {
    if (divisor.isZero()) {
      throw new IllegalArgumentException("Can't divide by zero");
    }
    // Some special cases
    if (this.isZero()) {
      return ZERO;
    }
    int cmp = this.compareTo(divisor);
    if (cmp < 0) {
      return this;
    }
    if (cmp == 0) {
      return ZERO;
    }
    UInt128 r = UInt128.ZERO;
    UInt128 n = this;
    for (int i = 0; i < SIZE; ++i) {
      r = r.shiftLeft();
      if (n.high < 0) {
        r = r.or(UInt128.ONE);
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
  public UInt128 pow(int exp) {
    if (exp < 0) {
      throw new IllegalArgumentException("exp must be >= 0");
    }

    // Mirrors algorithm in multiply(...)
    UInt128 result = UInt128.ONE;
    UInt128 base = this;

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
  public UInt128 isqrt() {
    // This is a decently performing isqrt given that the division operator is quite
    // slow (circa 200x slower than multiply).  If a faster division operator is
    // implemented, then the Newton-Raphson method with double approximation might
    // prove to be faster.
    UInt128 bit = UInt128.ONE.shiftLeft(SIZE - 2);

    // "bit" starts at the highest power of four <= this
    UInt128 num = this;
    while (bit.compareTo(num) > 0) {
      bit = bit.shiftRight(2);
    }

    UInt128 res = UInt128.ZERO;
    while (!bit.isZero()) {
      UInt128 rab = res.add(bit);
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
   * Shift {@code this} left 1 bit. A zero bit is moved into the rightmost bit.
   *
   * @return The result of shifting {@code this} left one bit.
   */
  public UInt128 shiftLeft() {
    int hh = (this.high << 1) | (this.midHigh >>> (Integer.SIZE - 1));
    int mh = (this.midHigh << 1) | (this.midLow >>> (Integer.SIZE - 1));
    int ml = (this.midLow << 1) | (this.low >>> (Integer.SIZE - 1));
    int ll = this.low << 1;
    return UInt128.from(hh, mh, ml, ll);
  }

  /**
   * Shift {@code this} left n bits. A zero bit is moved into the rightmost bits.
   *
   * @return The result of shifting {@code this} left n bits.
   */
  public UInt128 shiftLeft(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftRight(-n); // -ve left shift is right shift
    }
    long h = (n >= Long.SIZE) ? this.getLow() : this.getHigh();
    long l = (n >= Long.SIZE) ? 0 : this.getLow();
    int r = n % Long.SIZE;
    if (r > 0) {
      long c = l >>> (Long.SIZE - r);
      h = (h << r) | c;
      l <<= r;
    }
    return UInt128.from(h, l);
  }

  /**
   * Logical shift {@code this} right 1 bit. Zeros are shifted into the leftmost bit.
   *
   * @return The result of logical shifting {@code this} right one bit.
   */
  public UInt128 shiftRight() {
    int hh = this.high >>> 1;
    int mh = (this.midHigh >>> 1) | (this.high << (Integer.SIZE - 1));
    int ml = (this.midLow >>> 1) | (this.midHigh << (Integer.SIZE - 1));
    int ll = (this.low >>> 1) | (this.midLow << (Integer.SIZE - 1));
    return UInt128.from(hh, mh, ml, ll);
  }

  /**
   * Logical shift {@code this} right n bits. Zeros are shifted into the leftmost bits.
   *
   * @return The result of logical shifting {@code this} right n bits.
   */
  public UInt128 shiftRight(int n) {
    if (n == 0) {
      return this;
    } else if (n >= SIZE || n <= -SIZE) {
      return ZERO; // All bits are gone
    } else if (n < 0) {
      return shiftLeft(-n); // -ve right shift is left shift
    }
    long h = (n >= Long.SIZE) ? 0L : this.getHigh();
    long l = (n >= Long.SIZE) ? this.getHigh() : this.getLow();
    int r = n % Long.SIZE;
    if (r > 0) {
      long c = h << (Long.SIZE - r);
      h >>>= r;
      l = (l >>> r) | c;
    }
    return UInt128.from(h, l);
  }

  /**
   * Return the value of {@code ~this}.
   *
   * @return The logical inverse of {@code this}.
   */
  public UInt128 invert() {
    return UInt128.from(~this.high, ~this.midHigh, ~this.midLow, ~this.low);
  }

  @Override
  public int compareTo(UInt128 n) {
    int cmp = Long.compareUnsigned(this.getHigh(), n.getHigh());
    if (cmp == 0) {
      cmp = Long.compareUnsigned(this.getLow(), n.getLow());
    }
    return cmp;
  }

  /**
   * Return the most significant word.
   *
   * @return the most significant word.
   */
  public long getHigh() {
    return ((this.high & INT_MASK) << Integer.SIZE) | (this.midHigh & INT_MASK);
  }

  /**
   * Return the least significant word.
   *
   * @return the least significant word.
   */
  public long getLow() {
    return ((this.midLow & INT_MASK) << Integer.SIZE) | (this.low & INT_MASK);
  }

  /**
   * Calculates the bitwise inclusive-or of {@code this} with {@code other} ({@code this | other}).
   *
   * @param other The value to inclusive-or with {@code this}.
   * @return {@code this | other}
   */
  public UInt128 or(UInt128 other) {
    return UInt128.from(
        this.high | other.high,
        this.midHigh | other.midHigh,
        this.midLow | other.midLow,
        this.low | other.low);
  }

  /**
   * Calculates the bitwise and of {@code this} with {@code other} ({@code this & other}).
   *
   * @param other The value to and with {@code this}.
   * @return {@code this & other}
   */
  public UInt128 and(UInt128 other) {
    return UInt128.from(
        this.high & other.high,
        this.midHigh & other.midHigh,
        this.midLow & other.midLow,
        this.low & other.low);
  }

  /**
   * Calculates the exclusive-or of {@code this} with {@code other} ({@code this ^ other}).
   *
   * @param other The value to exclusive-or with {@code this}.
   * @return {@code this ^ other}
   */
  public UInt128 xor(UInt128 other) {
    return UInt128.from(
        this.high ^ other.high,
        this.midHigh ^ other.midHigh,
        this.midLow ^ other.midLow,
        this.low ^ other.low);
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
   *   <li>floor(log<sub>2</sub>(x)) = {@code 127 - numberOfLeadingZeros(x)}
   *   <li>ceil(log<sub>2</sub>(x)) = {@code 128 - numberOfLeadingZeros(x - 1)}
   * </ul>
   *
   * @return the number of zero bits preceding the highest-order ("leftmost") one-bit in the two's
   *     complement binary representation of the specified {@code long} value, or 128 if the value
   *     is equal to zero.
   */
  public int numberOfLeadingZeros() {
    long highValue = this.getHigh();
    return (highValue == 0)
        ? Long.SIZE + Long.numberOfLeadingZeros(this.getLow())
        : Long.numberOfLeadingZeros(highValue);
  }

  /**
   * Returns the index of the rightmost (lowest-order) one bit in this UInt128 (the number of zero
   * bits to the right of the rightmost one bit). Returns -1 if this UInt128 contains no one bits.
   * (Computes {@code (this == 0? -1 : log2(this & -this))}.)
   *
   * @return index of the rightmost one bit in this BigInteger.
   */
  public int getLowestSetBit() {
    // Mirrors java.math.BigInteger#getLowestSetBit()
    if (isZero()) {
      return -1;
    }
    int trailingZeros = Long.numberOfTrailingZeros(this.getLow());
    if (trailingZeros == Long.SIZE) {
      // Low part is zero
      trailingZeros += Long.numberOfTrailingZeros(this.getHigh());
    }
    return trailingZeros;
  }

  /**
   * Return {@code true} if the {@link UInt128} has its high bit set.
   *
   * @return {@code true} if the {@link UInt128} has its high bit set.
   */
  public boolean isHighBitSet() {
    return this.high < 0;
  }

  /**
   * Return {@code true} if {@code this} is zero.
   *
   * @return {@code true} if {@code this} is zero.
   */
  public boolean isZero() {
    return this.high == 0 && this.midHigh == 0 && this.midLow == 0 && this.low == 0;
  }

  /**
   * Return {@code true} if {@code this} is an even number.
   *
   * @return {@code true} if {@code this} is an even number.
   */
  public boolean isEven() {
    return (this.low & 1) == 0;
  }

  /**
   * Return {@code true} if {@code this} is an odd number.
   *
   * @return {@code true} if {@code this} is an odd number.
   */
  public boolean isOdd() {
    return (this.low & 1) != 0;
  }

  @Override
  public int hashCode() {
    return this.high * 31 + this.midHigh * 23 + this.midLow * 13 + this.low;
  }

  @Override
  public boolean equals(Object obj) {
    // Note that this needs to be consistent with compareTo
    if (this == obj) {
      return true;
    }
    if (obj instanceof UInt128) {
      UInt128 other = (UInt128) obj;
      return this.high == other.high
          && this.midHigh == other.midHigh
          && this.midLow == other.midLow
          && this.low == other.low;
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
    UInt128 n = this;
    UInt128 r = UInt128.from(radix);
    while (!n.isZero()) {
      UInt128 digit = n.remainder(r);
      sb.append(Character.forDigit(digit.low, radix));
      n = n.divide(r);
    }
    return sb.reverse().toString();
  }
}
