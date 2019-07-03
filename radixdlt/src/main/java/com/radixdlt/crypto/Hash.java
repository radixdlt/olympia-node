package com.radixdlt.crypto;

import com.radixdlt.utils.Bytes;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;

import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.common.EUID;

public final class Hash implements Comparable<Hash> {

	private static final Comparator<byte[]> COMPARATOR = UnsignedBytes.lexicographicalComparator();
	private static final SecureRandom secureRandom = new SecureRandom();
	private static HashHandler hasher = new SHAHashHandler();

	public static final int BYTES = 32;
    public static final int BITS = BYTES * Byte.SIZE;
    public static final Hash ZERO_HASH = new Hash(new byte[BYTES]);

    public static Hash random() {
		byte[] randomBytes = new byte[BYTES];

		secureRandom.nextBytes(randomBytes);

		return new Hash(hasher.hash256(randomBytes));
	}

	public static byte[] hash256(byte[] data)	{
		return hasher.hash256(data, 0, data.length);
	}

	public static byte[] hash256(byte[] data, int offset, int length) {
		return hasher.hash256(data, offset, length);
	}

	public static byte[] hash512(byte[] data) {
		return hasher.hash512(data, 0, data.length);
	}

	public static byte[] hash512(byte[] data, int offset, int length) {
		return hasher.hash512(data, offset, length);
	}

	public static byte[] hash256(byte[] data0, byte[] data1) {
		return hasher.hash256(data0, data1);
	}

	private final byte[] 	data;
	private EUID			id;

	// Hashcode caching
	private boolean hashCodeComputed = false;
	private int hashCode;

	public Hash(byte[] hash) {
		this(hash, 0, BYTES);
	}

	public Hash(byte[] hash, int offset, int length) {
		if (length != BYTES) {
			throw new IllegalArgumentException("Digest length must be " + BYTES + " bytes for Hash, was " + length);
		}
		if (offset + length > hash.length) {
			throw new IllegalArgumentException(String.format(
				"Hash length must be at least %s for offset %s, but was %s", offset + length, offset, hash.length));
		}

		this.data = new byte[BYTES];
		System.arraycopy(hash, offset, this.data, 0, BYTES);
	}

	public Hash(String hex) {
		if (hex.length() != (BYTES * 2)) {
			throw new IllegalArgumentException(String.format(
				"Digest length must be %s hex characters for Hash, was %s", BYTES * 2, hex.length()));
		}

		this.data = Bytes.fromHexString(hex);
	}

	/**
	 * Retrieve the hash bytes.
	 * <p>
	 * Note that for performance reasons, the underlying array is returned.
	 * If callers are passing this array to mutating methods, a copy should
	 * be taken.
	 *
	 * @return The hash data
	 */
	public byte[] toByteArray() {
		return this.data;
	}

	public void copyTo(byte[] array, int offset) {
		copyTo(array, offset, BYTES);
	}

	public void copyTo(byte[] array, int offset, int length) {
		if (array.length - offset < BYTES) {
			throw new IllegalArgumentException(String.format(
				"Array must be bigger than offset + %d but was %d", BYTES, array.length));
		}
		System.arraycopy(this.data, 0, array, offset, length);
	}

	public EUID getID() {
		if (id == null) {
			id = new EUID(data, 0);
		}
		return id;
	}

	public byte getFirstByte() {
		return data[0];
	}

	@Override
	public int compareTo(Hash object) {
		return COMPARATOR.compare(this.data, object.data);
	}

	@Override
	public String toString () {
		return Bytes.toHexString(this.data);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;

		if (o instanceof Hash) {
			Hash other = (Hash) o;

			if (this.hashCode() == other.hashCode()) {
				return Arrays.equals(this.data, other.data);
			}
		}

		return false;
	}

	@Override
	public int hashCode() {
		if (!this.hashCodeComputed) {
			this.hashCode = Arrays.hashCode(this.data);
			this.hashCodeComputed = true;
		}
		return this.hashCode;
	}
}
