package org.radix.common.ID;

import java.util.Objects;

import org.radix.utils.Int128;
import org.radix.utils.primitives.Bytes;

public final class EUID implements Comparable<EUID> {
	public static EUID valueOf(String string) {
		return new EUID(string);
	}

	public static final EUID ZERO = new EUID(0);
	public static final EUID ONE  = new EUID(1);
	public static final EUID TWO  = new EUID(2);

	/** Size in bytes. */
	public static final int BYTES = Int128.BYTES;

	private final Int128 value;

	/**
	 * Construct {@link EUID} from a {@code String} value.
	 * Hex conversion is performed.
	 *
	 * @param s The string to convert to an EUID.
	 * @throws IllegalArgumentException If {@code s} does not contain a valid
	 * 			{@code EUID}.
	 */
	public EUID(String s) {
		final byte[] bytes = Bytes.fromHexString(s);

		if (bytes.length != BYTES) {
			throw new IllegalArgumentException("Byte length must be " + BYTES + " but was " + bytes.length);
		}

		this.value = Int128.from(bytes);
	}

	public EUID(int value) {
		this((long) value);
	}

	public EUID(long value) {
		this.value = Int128.from(value);
	}

	public EUID(Int128 value) {
		this.value = value;
	}

	/**
	 * Constructor for creating an {@link EUID} from an array of bytes.
	 * The array is most-significant byte first, and must not be
	 * zero length.
	 * <p>If the array is smaller than {@link #BYTES}, then it is effectively
	 * padded with leading bytes with the correct sign.
	 * <p>If the array is longer than {@link #BYTES}, then values at index
	 * {@link #BYTES} and beyond are ignored.
	 *
	 * @param bytes The array of bytes to be used.
	 * @throws IllegalArgumentException if {@code bytes} is 0 bytes in length.
	 * @see #toByteArray()
	 */
	public EUID(byte[] bytes) {
		Objects.requireNonNull(bytes);
		if (bytes.length == 0) {
			throw new IllegalArgumentException("Invalid byte length of " + bytes.length);
		}
		this.value = Int128.from(bytes);
	}

	/**
	 * Constructor for creating an {@link EUID} from an array of bytes.
	 * The array is most-significant byte first.
	 *
	 * @param bytes The array of bytes to be used.
	 * @param offset The offset of the bytes to be used.
	 * @see #toByteArray()
	 */
	public EUID(byte[] bytes, int offset) {
		Objects.requireNonNull(bytes);
		this.value = Int128.from(bytes, offset);
	}

	/**
	 * Retrieve the shard number of this {@link EUID}.
	 *
	 * @return The shard number of this {@link EUID}.
	 */
	public long getShard() {
		return value.getHigh();
	}

	/**
	 * Retrieve the lower 64-bit word of this {@link EUID}.
	 *
	 * @return The lower 64-bit word of this {@link EUID}.
	 */
	public long getLow() {
		return value.getLow();
	}

	/**
	 * Calculate the routing difference between this {@link EUID} and the
	 * specified {@link EUID}.
	 * <p>
	 * Currently the algorithm is {@code numberOfLeadingZeros(this ^ other)}.
	 *
	 * @param other The {@link EUID} for which to calculate the routing
	 * 				distance from.
	 * @return The routing distance.
	 */
	public int routingDistanceFrom(EUID other) {
		return this.value.xor(other.value).numberOfLeadingZeros();
	}

	/**
	 * Compare the distances of {@code first} and {@code second} from
	 * {@code this}, treating our unsigned 128-bit number system as a
	 * ring with values 0 and 2<sup>128</sup>-1 adjacent.
	 * <p>
	 * The algorithm for calculating distance between two nodes is:
	 * <blockquote>
	 *   distance = min<sub>unsigned</sub>((a - b) mod 2<sup>128</sup>, (b - a) mod 2<sup>128</sup>)
	 * </blockquote>
	 *
	 * @param first  The first ID to calculate distance
	 * @param second The second ID to calculate the distance
	 * @return 0 if both distances are equal, 1 if first is further
	 * 			away and -1 if first is closer.
	 */
	public int compareDistances(EUID first, EUID second) {
		// Trivial check now for early exit.
		if (first.equals(second)) {
			return 0;
		}
		Int128 dFirst  = ringClosest(this.value, first.value);
		Int128 dSecond = ringClosest(this.value, second.value);
		return dFirst.compareToUnsigned(dSecond);
	}

	public byte[] toByteArray() {
		return value.toByteArray();
	}

	public byte[] toByteArray(byte[] bytes, int offset) {
		return value.toByteArray(bytes, offset);
	}

	@Override
	public int compareTo(EUID euid) {
		return this.value.compareToUnsigned(euid.value);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (o instanceof EUID) {
			EUID other = (EUID) o;
			return this.value.equals(other.value);
		}
		throw new UnsupportedOperationException("EUID equals object NOT an EUID");
	}

	@Override
	public int hashCode() {
		return this.value.hashCode();
	}

	@Override
	public String toString() {
		return Bytes.toHexString(value.toByteArray());
	}

	private static Int128 ringClosest(Int128 a, Int128 b) {
		// Here we assume that our (unsigned) number system forms a ring,
		// with 0 and 2^128-1 adjacent.
		// Given this, there are two ways to get from a to b, one clockwise
		// around the ring, the other anti-clockwise (for some suitable
		// definition of cw/acw).
		// These distances are given by (a - b) mod 2^128
		// and                          (b - a) mod 2^128
		// Where mod 2^128 is implicit given that we are working with
		// 128 bit integers.
		Int128 d1 = b.subtract(a);
		Int128 d2 = a.subtract(b);
		return d1.compareToUnsigned(d2) <= 0 ? d1 : d2;
	}
}
