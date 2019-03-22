package org.radix.utils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Utility methods for converting to/from {@link UInt256}.
 *
 * @author msandiford
 */
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
	 * Returns the specified {@link UInt256} as a {@link BigDecimal}.
	 *
	 * @param value The value to convert
	 * @return The value as a {@link BigDecimal}
	 */
	public static BigDecimal toBigDecimal(UInt256 value) {
		return new BigDecimal(toBigInteger(value));
	}

	/**
	 * Returns the specified {@link BigInteger} as a {@link UInt256}.
	 *
	 * @param value The value to convert
	 * @return The value as a {@link UInt256}
	 * @throws IllegalArgumentException if {@code value} &lt; 0, or {@code value} &gt; {@link UInt256#MAX_VALUE}.
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
	 * @throws IllegalArgumentException if {@code value} &lt; 0, or {@code value} &gt; {@link UInt256#MAX_VALUE}.
	 */
	public static UInt256 fromBigDecimal(BigDecimal value) {
		return fromBigInteger(value.toBigIntegerExact());
	}

    /**
     * Returns the smaller of two {@code UInt256} values. That is, the result
     * the argument closer to the value of {@link UInt256#MIN_VALUE}.  If the
     * arguments have the same value, the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
	public static UInt256 min(UInt256 a, UInt256 b) {
		int cmp = a.compareTo(b);
		return (cmp <= 0) ? a : b;
	}

    /**
     * Returns the larger of two {@code UInt256} values. That is, the result
     * the argument closer to the value of {@link UInt256#MAX_VALUE}.  If the
     * arguments have the same value, the result is that same value.
     *
     * @param   a   an argument.
     * @param   b   another argument.
     * @return  the smaller of {@code a} and {@code b}.
     */
	public static UInt256 max(UInt256 a, UInt256 b) {
		int cmp = a.compareTo(b);
		return (cmp >= 0) ? a : b;
	}
}
