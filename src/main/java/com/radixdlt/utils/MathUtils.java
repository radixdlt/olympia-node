package com.radixdlt.utils;

import com.radixdlt.SecurityCritical;
import java.util.Objects;

/**
 * Set of general mathematical utilities
 */
@SecurityCritical
public final class MathUtils {
	private MathUtils() {
		throw new IllegalStateException("Cannot instantiate");
	}

	/**
	 * Euclid's algorithm to find greatest common divisor
	 * @param x first number
	 * @param y second number
	 * @return greatest common divisor between x and y
	 */
	private static UInt128 gcd(UInt128 x, UInt128 y) {
		return (y.isZero()) ? x : gcd(y, x.remainder(y));
	}

	/**
	 * Euclid's algorithm to find greatest common divisor
	 * @param x first number
	 * @param y second number
	 * @return greatest common divisor between x and y
	 */
	private static UInt256 gcd(UInt256 x, UInt256 y) {
		return (y.isZero()) ? x : gcd(y, x.remainder(y));
	}

	/**
	 * Least common multiple computed via reduction by gcd
	 * @param x first number
	 * @param y second number
	 * @return least common multiple between x and y
	 */
	private static UInt128 lcm(UInt128 x, UInt128 y) {
		UInt128 d = y.divide(gcd(x, y));
		UInt128 r = x.multiply(d);
		boolean overflow = !x.isZero() && !r.divide(x).equals(d);
		return overflow ? null : r;
	}


	/**
	 * Least common multiple computed via reduction by gcd
	 * @param x first number
	 * @param y second number
	 * @return least common multiple between x and y
	 */
	private static UInt256 lcm(UInt256 x, UInt256 y) {
		UInt256 d = y.divide(gcd(x, y));
		UInt256 r = x.multiply(d);
		boolean overflow = !x.isZero() && !r.divide(x).equals(d);
		return overflow ? null : r;
	}

	/**
	 * Returns a capped least common multiple between an array of UInt128 numbers.
	 * The cap acts as a ceiling. If the result exceeds cap, then this will return
	 * null.
	 *
	 * numbers must be non-null and non-zero. Otherwise, result is undefined.
	 *
	 * @param cap the cap to be used for the computation
	 * @param numbers array of numbers of size atleast 1
	 * @return null, if the least common multiple is greater than cap, otherwise the least common multiple
	 * @throws ArrayIndexOutOfBoundsException if numbers is a zero length array
	 * @throws NullPointerException if cap or numbers is null
	 */
	public static UInt128 cappedLCM(UInt128 cap, UInt128... numbers) {
		Objects.requireNonNull(cap);
		UInt128 r = numbers[0];
		for (int i = 1; i < numbers.length; i++) {
			r = lcm(r, numbers[i]);
			if (r == null || r.compareTo(cap) > 0) {
				return null;
			}
		}
		return r;
	}

	/**
	 * Returns a capped least common multiple between an array of UInt128 numbers.
	 * The cap acts as a ceiling. If the result exceeds cap, then this will return
	 * null.
	 *
	 * numbers must be non-null and non-zero. Otherwise, result is undefined.
	 *
	 * @param cap the cap to be used for the computation
	 * @param numbers array of numbers of size atleast 1
	 * @return null, if the least common multiple is greater than cap, otherwise the least common multiple
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
