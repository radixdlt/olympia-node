package org.radix.utils;

import java.util.Arrays;
import java.util.Objects;

/**
 * A 256-bit signed integer, with comparison and some basic arithmetic
 * operations.
 *
 * @author msandiford
 */
public final class UInt256 implements Comparable<UInt256> {
	// Some sizing constants in line with Integer, Long etc
	/**
	 * Size of this numeric type in bits.
	 */
	public static final int SIZE = Int128.SIZE * 2;

	/**
	 * Size of this numeric type in bytes.
	 */
	public static final int BYTES = Int128.BYTES * 2;

	/**
	 * A constant holding the minimum value an {@code Int256} can
	 * have, -2<sup>255</sup>.
	 */
	public static final UInt256 MIN_VALUE = new UInt256(Int128.ZERO, Int128.ZERO);

	/**
	 * A constant holding the maximum value an {@code Int256} can
	 * have, 2<sup>255</sup>-1.
	 */
	public static final UInt256 MAX_VALUE = new UInt256(Int128.MINUS_ONE, Int128.MINUS_ONE);

	// Some commonly used values
	public static final UInt256 ZERO      = new UInt256(Int128.ZERO, Int128.ZERO);
	public static final UInt256 ONE       = new UInt256(Int128.ZERO, Int128.ONE);
	public static final UInt256 TWO       = new UInt256(Int128.ZERO, Int128.TWO);
	public static final UInt256 THREE     = new UInt256(Int128.ZERO, Int128.THREE);
	public static final UInt256 FOUR      = new UInt256(Int128.ZERO, Int128.FOUR);
	public static final UInt256 FIVE      = new UInt256(Int128.ZERO, Int128.FIVE);
	public static final UInt256 SIX       = new UInt256(Int128.ZERO, Int128.SIX);
	public static final UInt256 SEVEN     = new UInt256(Int128.ZERO, Int128.SEVEN);
	public static final UInt256 EIGHT     = new UInt256(Int128.ZERO, Int128.EIGHT);
	public static final UInt256 NINE      = new UInt256(Int128.ZERO, Int128.NINE);
	public static final UInt256 TEN       = new UInt256(Int128.ZERO, Int128.TEN);

	// Numbers in order.  This is used by factory methods.
	private static final UInt256[] NUMBERS = {
		ZERO, ONE, TWO, THREE, FOUR, FIVE, SIX, SEVEN, EIGHT, NINE, TEN
	};

	// The actual value.
	// @PackageLocalForTest
	private final Int128 high;
	// @PackageLocalForTest
	private final Int128 low;

	/**
	 * Factory method for materialising an {@link UInt256} from a {@code short}
	 * value.
	 *
	 * @param value The value to be represented as an {@link UInt256}.
	 * @return {@code value} as an {@link UInt256} type.
	 */
	public static UInt256 from(short value) {
		return from((long) value);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from an {@code int} value.
	 *
	 * @param value The value to be represented as an {@link UInt256}.
	 * @return {@code value} as an {@link UInt256} type.
	 */
	public static UInt256 from(int value) {
		return from((long) value);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from a {@code long} value.
	 * Note that values are sign extended into the 256 bit value.
	 *
	 * @param value The value to be represented as an {@link UInt256}.
	 * @return {@code value} as an {@link UInt256} type.
	 * @throws IllegalArgumentException if {@code value} is negative
	 */
	public static UInt256 from(long value) {
		if (value < 0) {
			throw new IllegalArgumentException("value must be > 0: " + value);
		}
		return from(Int128.from(value));
	}

	/**
	 * Factory method for materialising an {@link UInt256} from an {@link Int128}
	 * value.
	 *
	 * @param value  The least significant word of the value.
	 * @return the specified value as an {@link UInt256} type.
	 * @throws IllegalArgumentException if {@code value} is negative
	 */
	public static UInt256 from(Int128 value) {
		if (value.isNegative()) {
			throw new IllegalArgumentException("value must be > 0: " + value);
		}
		return from(Int128.ZERO, value);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from an {@link Int128}
	 * value.
	 * <p>
	 * <strong>
	 *   Note that the value of {@code high} is treated as an unsigned value.
	 * </strong>
	 *
	 * @param high The most significant word of the value.
	 * @param low The least significant word of the value.
	 * @return the specified values as an {@link UInt256} type.
	 */
	public static UInt256 from(Int128 high, Int128 low) {
		return new UInt256(high, low);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from an array
	 * of bytes.  The array is most-significant byte first, and must not be
	 * zero length.
	 * <p>If the array is smaller than {@link #BYTES}, then it is effectively
	 * padded with leading bytes with the correct sign.
	 * <p>If the array is longer than {@link #BYTES}, then values at index
	 * {@link #BYTES} and beyond are ignored.
	 *
	 * @param bytes The array of bytes to be used.
	 * @return {@code bytes} as an {@link UInt256} type.
	 * @throws IllegalArgumentException if {@code bytes} is 0 length.
	 * @see #toByteArray()
	 */
	public static UInt256 from(byte[] bytes) {
		Objects.requireNonNull(bytes);
		if (bytes.length ==  0) {
			throw new IllegalArgumentException("bytes is 0 bytes long");
		}
		byte[] newBytes = extend(bytes);
		Int128 high = Int128.from(newBytes, 0);
		Int128 low  = Int128.from(newBytes, Int128.BYTES);
		return UInt256.from(high, low);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from an array
	 * of bytes.  The array is most-significant byte first.
	 *
	 * @param bytes The array of bytes to be used.
	 * @param offset The offset within the array to be used.
	 * @return {@code bytes} from {@code offset} as an {@link UInt256} type.
	 * @see #toByteArray()
	 */
	public static UInt256 from(byte[] bytes, int offset) {
		Int128 high = Int128.from(bytes, offset);
		Int128 low  = Int128.from(bytes, offset + Int128.BYTES);
		return from(high, low);
	}

	/**
	 * Factory method for materialising an {@link UInt256} from a string.
	 * Conversion is performed base 10 and leading sign characters are
	 * permitted.
	 *
	 * @param s The array of bytes to be used.
	 * @return {@code s} as an {@link UInt256} type.
	 * @throws NumberFormatException if {@code s} is not a valid
	 * 			integer number.
	 */
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
				result = result.multiply(UInt256.TEN).add(NUMBERS[digit]);
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
		Arrays.fill(newBytes, 0, newPos, (byte) 0);
		System.arraycopy(bytes, 0, newBytes, newPos, bytes.length);
		return newBytes;
	}

	private UInt256(Int128 high, Int128 low) {
		this.high = high;
		this.low = low;
	}

	/**
	 * Converts {@code this} to an array of bytes.
	 * The most significant byte will be returned in index zero.
	 * The array will always be {@link #BYTES} bytes long, and
	 * will be zero filled to suit the actual value.
	 *
	 * @return An array of {@link #BYTES} bytes representing the
	 * 			value of this {@link UInt256}.
	 */
	public byte[] toByteArray() {
		return toByteArray(new byte[BYTES], 0);
	}

	/**
	 * Converts {@code this} to an array of bytes.
	 * The most significant byte will be returned in index {@code offset}.
	 * The array must be at least {@code offset + BYTES} long.
	 *
	 * @param bytes The array to place the bytes in.
	 * @param offset The offset within the array to place the bytes.
	 * @return The passed-in value of {@code bytes}.
	 */
	public byte[] toByteArray(byte[] bytes, int offset) {
		this.high.toByteArray(bytes, offset);
		this.low.toByteArray(bytes, offset + Int128.BYTES);
		return bytes;
	}

	/**
	 * Adds {@code other} to {@code this}, returning the result.
	 *
	 * @param other The addend.
	 * @return An {@link UInt256} with the value {@code this + other}.
	 */
	public UInt256 add(UInt256 other) {
		Int128 newLow = this.low.add(other.low);
		// Hacker's Delight section 2-13:
		// "The following branch-free code can be used to compute the
		// overflow predicate for unsigned add/subtract, with the result
		// being in the sign position."
		// Note that the use of method calls and the ternary operator
		// very likely precludes this from being branch-free in java.
		Int128 carry = this.low.logicalShiftRight()
				.add(other.low.logicalShiftRight())
				.add(this.low.and(other.low).and(Int128.ONE))
				.isNegative() ? Int128.ONE : Int128.ZERO;
		Int128 newHigh = this.high.add(other.high).add(carry);
		return UInt256.from(newHigh, newLow);
	}

	/**
	 * Subtracts {@code other} from {@code this}, returning the result.
	 *
	 * @param other The subtrahend.
	 * @return An {@link UInt256} with the value {@code this - other}.
	 */
	public UInt256 subtract(UInt256 other) {
		Int128 newLow = this.low.subtract(other.low);
		// Hacker's Delight section 2-13:
		// "The following branch-free code can be used to compute the
		// overflow predicate for unsigned add/subtract, with the result
		// being in the sign position."
		// Note that the use of method calls and the ternary operator
		// very likely precludes this from being branch-free in java.
		Int128 carry = this.low.logicalShiftRight()
				.subtract(other.low.logicalShiftRight())
				.subtract(this.low.invert().and(other.low).and(Int128.ONE))
				.isNegative() ? Int128.ONE : Int128.ZERO;
		Int128 newHigh = this.high.subtract(other.high).subtract(carry);
		return UInt256.from(newHigh, newLow);
	}

	/**
	 * Increments {@code this}.  Equivalent to {@code this.add(Int256.ONE)}, but
	 * faster.
	 *
	 * @return This number incremented by one.
	 */
	public UInt256 increment() {
		Int128 l = this.low.increment();
		Int128 h = l.isZero() ? this.high.increment() : this.high;
		return UInt256.from(h, l);
	}

	/**
	 * Decrements {@code this}.  Equivalent to {@code this.subtract(Int256.ONE)}, but
	 * faster.
	 *
	 * @return This number decremented by one.
	 */
	public UInt256 decrement() {
		Int128 l = this.low.decrement();
		Int128 h = this.low.isZero() ? this.high.decrement() : this.high;
		return UInt256.from(h, l);
	}

	/**
	 * Multiplies {@code this} by the specified multiplicand.
	 *
	 * @param multiplicand The multiplicand to multiply {@code this} by.
	 * @return The result {@code this * multiplicand}.
	 */
	public UInt256 multiply(UInt256 multiplicand) {
		// Russian peasant
		UInt256 result = UInt256.ZERO;
		UInt256 multiplier = this;

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
	public UInt256 divide(UInt256 divisor) {
		if (divisor.isZero()) {
			throw new IllegalArgumentException("Can't divide by zero");
		}
		UInt256 q = UInt256.ZERO;
		UInt256 r = UInt256.ZERO;
		UInt256 n = this;
		for (int i = 0; i < SIZE; ++i) {
			r = r.shiftLeft();
			q = q.shiftLeft();
			if (n.high.isNegative()) {
				r = r.or(UInt256.ONE);
			}
			n = n.shiftLeft();
			if (r.compareTo(divisor) >= 0) {
				r = r.subtract(divisor);
				q = q.or(UInt256.ONE);
			}
		}
		return q;
	}

	/**
	 * Returns the remainder of the division of {@code this} by
	 * the specified divisor.
	 *
	 * @param divisor The divisor to divide {@code this} by.
	 * @return The remainder of the division {@code this / divisor}.
	 */
	public UInt256 remainder(UInt256 divisor) {
		if (divisor.isZero()) {
			throw new IllegalArgumentException("Can't divide by zero");
		}
		UInt256 r = UInt256.ZERO;
		UInt256 n = this;
		for (int i = 0; i < SIZE; ++i) {
			r = r.shiftLeft();
			if (n.high.isNegative()) {
				r = r.or(UInt256.ONE);
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
	 * Shifts {@code this} left 1 bit.  A zero bit is moved into the
	 * leftmost bit.
	 *
	 * @return The result of shifting {@code this} left one bit.
	 */
	public UInt256 shiftLeft() {
		Int128 h = this.high.shiftLeft();
		if (this.low.isNegative()) {
			h = h.or(Int128.ONE);
		}
		Int128 l = this.low.shiftLeft();
		return UInt256.from(h, l);
	}

	/**
	 * Shifts {@code this} right 1 bit.  A zero bit is moved into the
	 * into the rightmost bit.
	 *
	 * @return The result of arithmetic shifting {@code this} right one bit.
	 */
	public UInt256 shiftRight() {
		Int128 h = this.high.logicalShiftRight();
		Int128 l = this.low.logicalShiftRight();
		if (this.high.isOdd()) {
			l = l.or(Int128.MIN_VALUE);
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
		int cmp = this.high.compareToUnsigned(n.high);
		if (cmp == 0) {
			cmp = this.low.compareToUnsigned(n.low);
		}
		return cmp;
	}

	/**
	 * Returns the most significant word.
	 *
	 * @return the most significant word.
	 */
	public Int128 getHigh() {
		return this.high;
	}

	/**
	 * Returns the least significant word.
	 *
	 * @return the least significant word.
	 */
	public Int128 getLow() {
		return this.low;
	}

	/**
	 * Calculates the bitwise inclusive-or of {@code this} with {@code other}
	 * ({@code this | other}).
	 *
	 * @param other The value to inclusive-or with {@code this}.
	 * @return {@code this | other}
	 */
	public UInt256 or(UInt256 other) {
		return UInt256.from(this.high.or(other.high), this.low.or(other.low));
	}

	/**
	 * Calculates the bitwise and of {@code this} with {@code other}
	 * ({@code this & other}).
	 *
	 * @param other The value to and with {@code this}.
	 * @return {@code this & other}
	 */
	public UInt256 and(UInt256 other) {
		return UInt256.from(this.high.and(other.high), this.low.and(other.low));
	}

	/**
	 * Calculates the exclusive-or of {@code this} with {@code other}
	 * ({@code this ^ other}).
	 *
	 * @param other The value to exclusive-or with {@code this}.
	 * @return {@code this ^ other}
	 */
	public UInt256 xor(UInt256 other) {
		return UInt256.from(this.high.xor(other.high), this.low.xor(other.low));
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
	 * <li>floor(log<sub>2</sub>(x)) = {@code 255 - numberOfLeadingZeros(x)}
	 * <li>ceil(log<sub>2</sub>(x)) = {@code 256 - numberOfLeadingZeros(x - 1)}
	 * </ul>
	 *
	 * @return the number of zero bits preceding the highest-order
	 *     ("leftmost") one-bit in the two's complement binary representation
	 *     of the specified {@code long} value, or 256 if the value
	 *     is equal to zero.
	 */
	public int numberOfLeadingZeros() {
		return (this.high.isZero())
				? Int128.SIZE + this.low.numberOfLeadingZeros()
				: this.high.numberOfLeadingZeros();
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
		return (this.low.getLow() & 1L) == 0L;
	}

	/**
	 * Returns {@code true} if {@code this} is an odd number.
	 *
	 * @return {@code true} if {@code this} is an odd number.
	 */
	public boolean isOdd() {
		return (this.low.getLow() & 1L) != 0L;
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
			return this.high.equals(other.high) && this.low.equals(other.low);
		}
		return false;
	}

	@Override
	public String toString() {
		return toString(10);
	}

    /**
     * Returns a string representation of this object in the specified radix.
     * <p>
     * If the radix is smaller than {@code Character.MIN_RADIX} or larger than
     * {@code Character.MAX_RADIX}, an {@link IllegalArgumentException} is
     * thrown.
     * <p>
     * The characters of the result represent the magnitude of {@code this}.
     * If the magnitude is zero, it is represented by a single zero character
     * {@code '0'}; otherwise no leading zeros are output.
     * <p>
     * The following ASCII characters are used as digits:
     *
     * <blockquote>
     *   {@code 0123456789abcdefghijklmnopqrstuvwxyz}
     * </blockquote>
     *
     * If {@code radix} is <var>N</var>, then the first <var>N</var> of these
     * characters are used as radix-<var>N</var> digits in the order shown,
     * i.e. the digits for hexadecimal (radix 16) are {@code 0123456789abcdef}.
     *
     * @param   radix   the radix to use in the string representation.
     * @return  a string representation of the argument in the specified radix.
     * @see     Character#MAX_RADIX
     * @throws  IllegalArgumentException if {@code radix} is less than
     *          {@code Character.MIN_RADIX} or greater than
     *          {@code Character.MAX_RADIX}.
     * @see     Character#MIN_RADIX
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
			sb.append(Character.forDigit((int) digit.low.getLow(), radix));
			n = n.divide(r);
		}
		return sb.reverse().toString();
	}
}
