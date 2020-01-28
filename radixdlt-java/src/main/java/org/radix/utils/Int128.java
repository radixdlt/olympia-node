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

import java.util.Arrays;
import java.util.Objects;

import org.radix.utils.primitives.Longs;

/**
 * A 128-bit signed integer, with comparison and some basic arithmetic
 * operations.
 *
 * @author msandiford
 */
public final class Int128 extends Number implements Comparable<Int128> {
	private static final long serialVersionUID = 8627474700385282074L;

	// Constants used for doubleValue()
	// Values taken from
	// https://en.wikipedia.org/wiki/Double-precision_floating-point_format
	private static final int  SIGNIFICAND_PREC = 53; // Including implicit leading one bit
	private static final long SIGNIFICAND_MASK = (1L << (SIGNIFICAND_PREC - 1)) - 1L;
	private static final long SIGNIFICAND_OVF  = 1L << SIGNIFICAND_PREC;
	private static final int  EXPONENT_BIAS    = 1023;

	// Some sizing constants in line with Integer, Long etc
	/**
	 * Size of this numeric type in bits.
	 */
	public static final int SIZE = Long.SIZE * 2;
	/**
	 * Size of this numeric type in bytes.
	 */
	public static final int BYTES = Long.BYTES * 2;

	/**
     * A constant holding the minimum value an {@code Int128} can
     * have, -2<sup>127</sup>.
     */
    public static final Int128 MIN_VALUE = new Int128(0x8000_0000_0000_0000L, 0x0000_0000_0000_0000L);

    /**
     * A constant holding the maximum value an {@code Int128} can
     * have, 2<sup>127</sup>-1.
     */
    public static final Int128 MAX_VALUE = new Int128(0x7FFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL);

	// Some commonly used values
	public static final Int128 ZERO      = new Int128(0L, 0L);
	public static final Int128 ONE       = new Int128(0L, 1L);
	public static final Int128 TWO       = new Int128(0L, 2L);
	public static final Int128 THREE     = new Int128(0L, 3L);
	public static final Int128 FOUR      = new Int128(0L, 4L);
	public static final Int128 FIVE      = new Int128(0L, 5L);
	public static final Int128 SIX       = new Int128(0L, 6L);
	public static final Int128 SEVEN     = new Int128(0L, 7L);
	public static final Int128 EIGHT     = new Int128(0L, 8L);
	public static final Int128 NINE      = new Int128(0L, 9L);
	public static final Int128 TEN       = new Int128(0L, 10L);
	public static final Int128 MINUS_ONE = new Int128(-1L, -1L);

	// Numbers in order.  This is used by factory methods.
	private static final Int128[] NUMBERS = {
		ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
	};

	// The actual value.
	// @PackageLocalForTest
	private final long high;
	// @PackageLocalForTest
	private final long low;

	/**
	 * Factory method for materialising an {@link Int128} from a {@code short}
	 * value.
	 *
	 * @param value The value to be represented as an {@link Int128}.
	 * @return {@code value} as an {@link Int128} type.
	 */
	public static Int128 from(short value) {
		return from((long) value);
	}

	/**
	 * Factory method for materialising an {@link Int128} from an {@code int} value.
	 *
	 * @param value The value to be represented as an {@link Int128}.
	 * @return {@code value} as an {@link Int128} type.
	 */
	public static Int128 from(int value) {
		return from((long) value);
	}

	/**
	 * Factory method for materialising an {@link Int128} from a {@code long} value.
	 * Note that values are sign extended into the 128 bit value.
	 *
	 * @param value The value to be represented as an {@link Int128}.
	 * @return {@code value} as an {@link Int128} type.
	 */
	public static Int128 from(long value) {
		// Sign extend
		return from(value < 0 ? -1L : 0L, value);
	}

	/**
	 * Factory method for materialising an {@link Int128} from two {@code long}
	 * values. {@code high} is the most significant word, and {@code low} the least
	 * significant.
	 *
	 * @param high The most significant word of the 128 bit value.
	 * @param low  The least significant word of the 128 bit value.
	 * @return {@code (high << 64) | low} as an {@link Int128} type.
	 */
	public static Int128 from(long high, long low) {
		if (high == 0L) {
			if (low >= 0L && low < NUMBERS.length) {
				return NUMBERS[(int) low];
			}
		} else if (high == -1L) {
			if (low == -1L) {
				return MINUS_ONE;
			}
		}
		return new Int128(high, low);
	}

	/**
	 * Factory method for materialising an {@link Int128} from an array
	 * of bytes.  The array is most-significant byte first, and must not be
	 * zero length.
	 * <p>If the array is smaller than {@link #BYTES}, then it is effectively
	 * padded with leading bytes with the correct sign.
	 * <p>If the array is longer than {@link #BYTES}, then values at index
	 * {@link #BYTES} and beyond are ignored.
	 *
	 * @param bytes The array of bytes to be used.
	 * @return {@code bytes} as an {@link Int128} type.
	 * @throws IllegalArgumentException if {@code bytes} is 0 length.
	 * @see #toByteArray()
	 */
	public static Int128 from(byte[] bytes) {
		Objects.requireNonNull(bytes);
		if (bytes.length ==  0) {
			throw new IllegalArgumentException("bytes is 0 bytes long");
		}
		byte[] newBytes = extend(bytes);
		long high = Longs.fromByteArray(newBytes, 0);
		long low  = Longs.fromByteArray(newBytes, Long.BYTES);
		return Int128.from(high, low);
	}

	/**
	 * Factory method for materialising an {@link Int128} from an array
	 * of bytes.  The array is most-significant byte first.
	 *
	 * @param bytes The array of bytes to be used.
	 * @param offset The offset within the array to be used.
	 * @return {@code bytes} from {@code offset} as an {@link Int128} type.
	 * @see #toByteArray()
	 */
	public static Int128 from(byte[] bytes, int offset) {
		long high = Longs.fromByteArray(bytes, offset);
		long low  = Longs.fromByteArray(bytes, offset + Long.BYTES);
		return from(high, low);
	}

	/**
	 * Factory method for materialising an {@link Int128} from a string.
	 * Conversion is performed base 10 and leading sign characters are
	 * permitted.
	 *
	 * @param s The array of bytes to be used.
	 * @return {@code s} as an {@link Int128} type.
	 * @throws NumberFormatException if {@code s} is not a valid
	 * 			integer number.
	 */
	public static Int128 from(String s) {
		Objects.requireNonNull(s);

		int len = s.length();
		if (len > 0) {
			int i = 0;
			boolean negative = false;
			char ch = s.charAt(0);
			if (ch == '-') {
				negative = true;
				i += 1; // skip first char
			} else if (ch == '+') {
				i += 1; // skip first char
			}
			if (i >= len) {
				throw new NumberFormatException(s);
			}
			// No real effort to catch overflow here
			Int128 result = Int128.ZERO;
			while (i < len) {
				int digit = Character.digit(s.charAt(i++), 10);
				if (digit < 0) {
					throw new NumberFormatException(s);
				}
				result = result.multiply(Int128.TEN).add(NUMBERS[digit]);
			}
			return negative ? result.negate() : result;
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
		// Sign extension
		Arrays.fill(newBytes, 0, newPos, (bytes[0] < 0) ? (byte) 0xFF : (byte) 0x00);
		System.arraycopy(bytes, 0, newBytes, newPos, bytes.length);
		return newBytes;
	}

	private Int128(long high, long low) {
		this.high = high;
		this.low = low;
	}

	/**
	 * Convert value to an array of bytes.
	 * The most significant byte will be returned in index zero.
	 * The array will always be {@link #BYTES} bytes long, and
	 * will be zero filled to suit the actual value.
	 *
	 * @return An array of {@link #BYTES} bytes representing the
	 * 			value of this {@link Int128}.
	 */
	public byte[] toByteArray() {
		return toByteArray(new byte[BYTES], 0);
	}

	/**
	 * Convert value to an array of bytes.
	 * The most significant byte will be returned in index {@code offset}.
	 * The array must be at least {@code offset + BYTES} long.
	 *
	 * @param bytes The array to place the bytes in.
	 * @param offset The offset within the array to place the bytes.
	 * @return The passed-in value of {@code bytes}.
	 */
	public byte[] toByteArray(byte[] bytes, int offset) {
		Longs.copyTo(this.high, bytes, offset);
		Longs.copyTo(this.low,  bytes, offset + Long.BYTES);
		return bytes;
	}

	/**
	 * Add {@code other} to {@code this}, returning the result.
	 *
	 * @param other The addend.
	 * @return An {@link Int128} with the value {@code this + other}.
	 */
	public Int128 add(Int128 other) {
		long newLow = this.low + other.low;
		// Hacker's Delight section 2-13:
		// "The following branch-free code can be used to compute the
		// overflow predicate for unsigned add/subtract, with the result
		// being in the sign position."
		long carry = ((this.low >>> 1) + (other.low >>> 1) + ((this.low & other.low) & 1)) < 0 ? 1L : 0L;
		long newHigh = this.high + other.high + carry;
		return Int128.from(newHigh, newLow);
	}

	/**
	 * Subtract {@code other} from {@code this}, returning the result.
	 *
	 * @param other The subtrahend.
	 * @return An {@link Int128} with the value {@code this - other}.
	 */
	public Int128 subtract(Int128 other) {
		long newLow = this.low - other.low;
		// Hacker's Delight section 2-13:
		// "The following branch-free code can be used to compute the
		// overflow predicate for unsigned add/subtract, with the result
		// being in the sign position."
		long carry = ((this.low >>> 1) - (other.low >>> 1) - ((~this.low & other.low) & 1)) < 0 ? 1L : 0L;
		long newHigh = this.high - other.high - carry;
		return Int128.from(newHigh, newLow);
	}

	/**
	 * Multiply {@code this} by the specified multiplicand.
	 *
	 * @param multiplicand The multiplicand to multiply {@code this} by.
	 * @return The result {@code this * multiplicand}.
	 */
	public Int128 multiply(Int128 multiplicand) {
		// Russian peasant
		Int128 result = Int128.ZERO;
		Int128 multiplier = this;

		while (!multiplicand.isZero()) {
			if (multiplicand.isOdd()) {
				result = result.add(multiplier);
			}
			multiplier = multiplier.shiftLeft();
			multiplicand = multiplicand.logicalShiftRight();
		}
		return result;
	}

	/**
	 * Increment a number.  Equivalent to {@code this.add(Int128.ONE)}, but
	 * faster.
	 *
	 * @return This number incremented by one.
	 */
	public Int128 increment() {
		long l = this.low + 1;
		long h = (l == 0L) ? this.high + 1 : this.high;
		return Int128.from(h, l);
	}

	/**
	 * Decrement a number.  Equivalent to {@code this.subtract(Int128.ONE)}, but
	 * faster.
	 *
	 * @return This number decremented by one.
	 */
	public Int128 decrement() {
		long l = this.low - 1;
		long h = (l == -1L) ? this.high - 1 : this.high;
		return Int128.from(h, l);
	}

	/**
	 * Divide {@code this} by the specified divisor.
	 *
	 * @param divisor The divisor to divide {@code this} by.
	 * @return The result {@code floor(this / divisor)}.
	 */
	public Int128 divide(Int128 divisor) {
		boolean negative = (this.high ^ divisor.high) < 0;
		Int128 dividend = this.abs();
		divisor = divisor.abs();
		Int128 sum = Int128.ZERO;
		for (int cmp = dividend.compareToUnsigned(divisor); cmp >= 0; cmp = dividend.compareToUnsigned(divisor)) {
			Int128 quotient = Int128.ONE;
			Int128 div = divisor;
			Int128 next = divisor.shiftLeft();
			while (next.compareToUnsigned(dividend) <= 0) {
				div = next;
				next = next.shiftLeft();
				quotient = quotient.shiftLeft();
			}
			sum = sum.add(quotient);
			dividend = dividend.subtract(div);
		}
		return negative ? sum.negate() : sum;
	}

	/**
	 * Return the remainder of the division of {@code this} by
	 * the specified divisor.
	 *
	 * @param divisor The divisor to divide {@code this} by.
	 * @return The remainder of the division {@code this / divisor}.
	 */
	public Int128 remainder(Int128 divisor) {
		boolean negative = this.high < 0;
		Int128 result = remainder(this.abs(), divisor.abs());
		return negative ? result.negate() : result;
	}

	/**
	 * Shift {@code this} left 1 bit.  A zero bit is moved into the
	 * leftmost bit.
	 *
	 * @return The result of shifting {@code this} left one bit.
	 */
	public Int128 shiftLeft() {
		long h = (this.high << 1) | (this.low >>> (Long.SIZE - 1));
		long l = this.low << 1;
		return Int128.from(h, l);
	}

	/**
	 * Arithmetic shift {@code this} right 1 bit.  The current value
	 * of the sign bit is duplicated into the rightmost bit.
	 *
	 * @return The result of arithmetic shifting {@code this} right one bit.
	 */
	public Int128 shiftRight() {
		long h = this.high >> 1;
		long l = (this.low >>> 1) | (this.high << (Long.SIZE - 1));
		return Int128.from(h, l);
	}

	/**
	 * Logical shift {@code this} right 1 bit.  Zeros are shifted into
	 * the rightmost bit.
	 *
	 * @return The result of logical shifting {@code this} right one bit.
	 */
	public Int128 logicalShiftRight() {
		long h = this.high >>> 1;
		long l = (this.low >>> 1) | (this.high << (Long.SIZE - 1));
		return Int128.from(h, l);
	}

	/**
	 * Similar to {@link #shiftRight}, but rounds towards zero.
	 *
	 * @return The result of calculating {@code this / 2}.
	 */
	public Int128 div2() {
		long l = this.low;
		long h = this.high;
		if (h < 0) {
			l += 1;
			if (l == 0L) {
				h += 1;
			}
		}
		return Int128.from(h >> 1, (l >>> 1) | (h << (Long.SIZE - 1)));
	}

	/**
	 * Return the absolute value of {@code this}.
	 * <p>
	 * Note that, similarly to other two's complement numbers, the
	 * absolute value of the  maximal negative value is returned as
	 * itself.
	 *
	 * @return The absolute value of {@code this}.
	 */
	public Int128 abs() {
		return (this.high < 0) ? this.negate() : this;
	}

	/**
	 * Return the value of {@code 0 - this}.
	 * <p>
	 * Note that, similarly to other two's complement numbers, the
	 * negative value of the maximal negative value is returned as
	 * itself.
	 *
	 * @return The negative value of {@code this}.
	 */
	public Int128 negate() {
		// Two's complement
		long h = ~this.high;
		long l = ~this.low;
		l += 1;
		if (l == 0) {
			h += 1;
		}
		return Int128.from(h, l);
	}

	/**
	 * Return the value of {@code ~this}.
	 *
	 * @return The logical inverse of {@code this}.
	 */
	public Int128 invert() {
		return Int128.from(~this.high, ~this.low);
	}

	@Override
	public int compareTo(Int128 n) {
		int cmp = Long.compare(this.high, n.high);
		if (cmp == 0) {
			cmp = Longs.compareUnsigned(this.low, n.low);
		}
		return cmp;
	}

    /**
     * Compares {@code this} and {@code n} numerically treating the values
     * as unsigned.
     *
     * @param  n the second {@link Int128} to compare.
     * @return the value {@code 0} if {@code this == n}; a value less
     *         than {@code 0} if {@code this < n} as unsigned values; and
     *         a value greater than {@code 0} if {@code this > n} as
     *         unsigned values
     */
	public int compareToUnsigned(Int128 n) {
		int cmp = Longs.compareUnsigned(this.high, n.high);
		if (cmp == 0) {
			cmp = Longs.compareUnsigned(this.low, n.low);
		}
		return cmp;
	}

	/**
	 * Return the most significant word.
	 *
	 * @return the most significant word.
	 */
	public long getHigh() {
		return high;
	}

	/**
	 * Return the least significant word.
	 *
	 * @return the least significant word.
	 */
	public long getLow() {
		return low;
	}

	@Override
	public int intValue() {
		return (int) low;
	}

	@Override
	public long longValue() {
		return low;
	}

	@Override
	public float floatValue() {
		return (float) doubleValue();
	}

	@Override
	public double doubleValue() {
		// If it's a number that fits into a long, let the compiler
		// convert.
		if ((this.high == 0L && this.low >= 0L) || (this.high == -1L && this.low < 0L)) {
			return low;
		}

		long h = this.high;
		long l = this.low;
		boolean negative = h < 0;
		if (negative) {
			// Calculate two's complement
			h = ~h;
			l = ~l;
			l += 1;
			if (l == 0) {
				h += 1;
			}
		}

		// Must be at least 64 bits based on initial checks.
		// Note that it is not possible for this exponent to overflow a double
		// (128 < 1023).
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
		double value = Double.longBitsToDouble(raw);
		return negative ? -1.0 * value : value;
	}

	/**
	 * Calculates the bitwise inclusive-or of {@code this} with {@code other}
	 * ({@code this | other}).
	 *
	 * @param other The value to inclusive-or with {@code this}.
	 * @return {@code this | other}
	 */
	public Int128 or(Int128 other) {
		return Int128.from(this.high | other.high, this.low | other.low);
	}

	/**
	 * Calculates the bitwise and of {@code this} with {@code other}
	 * ({@code this & other}).
	 *
	 * @param other The value to and with {@code this}.
	 * @return {@code this & other}
	 */
	public Int128 and(Int128 other) {
		return Int128.from(this.high & other.high, this.low & other.low);
	}

	/**
	 * Calculates the exclusive-or of {@code this} with {@code other}
	 * ({@code this ^ other}).
	 *
	 * @param other The value to exclusive-or with {@code this}.
	 * @return {@code this ^ other}
	 */
	public Int128 xor(Int128 other) {
		return Int128.from(this.high ^ other.high, this.low ^ other.low);
	}

    /**
     * Returns the number of zero bits preceding the highest-order
     * ("leftmost") one-bit in the two's complement binary representation
     * of the specified {@code long} value.  Returns 128 if the
     * specified value has no one-bits in its two's complement representation,
     * in other words if it is equal to zero.
     *
     * <p>Note that this method is closely related to the logarithm base 2.
     * For all positive {@code long} values x:
     * <ul>
     * <li>floor(log<sub>2</sub>(x)) = {@code 127 - numberOfLeadingZeros(x)}
     * <li>ceil(log<sub>2</sub>(x)) = {@code 128 - numberOfLeadingZeros(x - 1)}
     * </ul>
     *
     * @return the number of zero bits preceding the highest-order
     *     ("leftmost") one-bit in the two's complement binary representation
     *     of the specified {@code long} value, or 128 if the value
     *     is equal to zero.
     */
	public int numberOfLeadingZeros() {
		return (this.high == 0)
				? Long.SIZE + Long.numberOfLeadingZeros(this.low)
				: Long.numberOfLeadingZeros(this.high);
	}

	/**
	 * Return {@code true} if the {@link Int128} is negative.
	 *
	 * @return {@code true} if the {@link Int128} is negative.
	 */
	public boolean isNegative() {
		return this.high < 0L;
	}

	/**
	 * Return {@code true} if {@code this} is zero.
	 *
	 * @return {@code true} if {@code this} is zero.
	 */
	public boolean isZero() {
		return this.high == 0 && this.low == 0;
	}

	/**
	 * Return {@code true} if {@code this} is an even number.
	 *
	 * @return {@code true} if {@code this} is an even number.
	 */
	public boolean isEven() {
		return (this.low & 1L) == 0L;
	}

	/**
	 * Return {@code true} if {@code this} is an odd number.
	 *
	 * @return {@code true} if {@code this} is an odd number.
	 */
	public boolean isOdd() {
		return (this.low & 1L) != 0L;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(this.high) * 31 + Long.hashCode(this.low);
	}

	@Override
	public boolean equals(Object obj) {
		// Note that this needs to be consistent with compareTo
		if (this == obj) {
			return true;
		}
		if (obj instanceof Int128) {
			Int128 other = (Int128) obj;
			return this.high == other.high && this.low == other.low;
		}
		return false;
	}

	@Override
	public String toString() {
		if (isZero()) {
			return "0";
		}
		// This is cheating to avoid the case where this.abs() < 0
		if (this.high == 0x8000_0000_0000_0000L && this.low == 0) {
			return "-170141183460469231731687303715884105728";
		}
		StringBuilder sb = new StringBuilder();
		boolean negative = (this.high < 0);
		Int128 n = this.abs();
		while (!n.isZero()) {
			Int128 digit = n.remainder(Int128.TEN);
			sb.append((char) ('0' + digit.low));
			n = n.divide(Int128.TEN);
		}
		if (negative) {
			sb.append('-');
		}
		return sb.reverse().toString();
	}

	private static int bitLength(long n) {
		return Long.SIZE - Long.numberOfLeadingZeros(n);
	}

	// Internal computation.  dividend and divisor assumed positive.
	private static Int128 remainder(Int128 dividend, final Int128 divisor) {
		while (true) {
			int cmp = dividend.compareToUnsigned(divisor);
			if (cmp == 0) {
				return Int128.ZERO;
			} else if (cmp < 0) {
				return dividend;
			}

			Int128 div = divisor;
			Int128 next = divisor.shiftLeft();
			while (next.compareToUnsigned(dividend) <= 0) {
				div = next;
				next = next.shiftLeft();
			}
			dividend = dividend.subtract(div);
		}
	}
}
