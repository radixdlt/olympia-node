/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import com.google.common.base.Suppliers;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.SecurityCritical;
import com.radixdlt.SecurityCritical.SecurityKind;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.Bytes;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

@SecurityCritical(SecurityKind.HASHING)
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

	/**
	 * Hashes the supplied array, returning a cryptographically secure 256-bit hash, in this container class.
	 * <p>
	 * If your data already is hashed and you want to create a container for it,
	 * use {@link Hash#Hash(byte[]) } constructor instead.
	 *
	 * @param dataToBeHashed The data to hash
	 * @return The digest by applying the 256-bit/32-byte hash function, in this container class.
	 */
	public static Hash of(byte[] dataToBeHashed)	{
		return new Hash(Hash.hash256(dataToBeHashed));
	}

	/**
	 * Hashes the supplied array, returning a cryptographically secure 256-bit hash.
	 * <p>
	 * If your data already is hashed and you want to create a container for it,
	 * use {@link Hash#Hash(byte[]) } constructor instead.
	 *
	 * @param dataToBeHashed The data to hash
	 * @return The digest by applying the 256-bit/32-byte hash function
	 */
	public static byte[] hash256(byte[] dataToBeHashed)	{
		return hash256(dataToBeHashed, 0, dataToBeHashed.length);
	}

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 256-bit hash.
	 * <p>
	 * If your data already is hashed and you want to create a container for it,
	 * use {@link Hash#Hash(byte[], int, int) } constructor instead.
	 *
	 * @param dataToBeHashed The data to hash
	 * @param offset The offset within the array to start hashing data
	 * @param length The number of bytes in the array to hash
	 * @return The digest by applying the 256-bit/32-byte hash function.
	 */
	public static byte[] hash256(byte[] dataToBeHashed, int offset, int length) {
		return hasher.hash256(dataToBeHashed, offset, length);
	}

	/**
	 * Hashes the specified portion of the array, returning a cryptographically secure 512-bit hash.
	 * <p>
	 * If your data already is hashed and you want to create a container for it,
	 * use {@link Hash#Hash(byte[]) } constructor instead.
	 *
	 * @param dataToBeHashed The data to hash
	 * @return The 512-bit/64-byte hash
	 */
	public static byte[] hash512(byte[] dataToBeHashed) {
		return hasher.hash512(dataToBeHashed);
	}


	private final byte[] data;
	private final transient Supplier<EUID> idCached = Suppliers.memoize(this::computeId);
	private final transient int hashCodeCached;

	/**
	 * This does NOT perform any hashing, the byte array passed should be already hashed.
	 * <p>
	 * If you want to hash the data, use any of the static methods
	 * {@link Hash#hash256(byte[])} or {@link Hash#hash512(byte[])} instead.
	 *
	 * @param alreadyHashedData The data that has already been hashed.
	 * @return a container for the already hashed data.
	 */
	public Hash(byte[] alreadyHashedData) {
		this(alreadyHashedData, 0, BYTES);
	}

	/**
	 * This does NOT perform any hashing, the byte array passed should be already hashed.
	 * <p>
	 * If you want to hash the data, use any of the static methods
	 * {@link Hash#hash256(byte[])} or {@link Hash#hash512(byte[])} instead.
	 *
	 * @param alreadyHashedData The data that has already been hashed.
	 * @param offset The offset within the already hashed data
	 * @param length The number of bytes of the already hashed data.
	 * @return a container for the already hashed data.
	 */
	public Hash(byte[] alreadyHashedData, int offset, int length) {
		Objects.requireNonNull(alreadyHashedData);
		if (length != BYTES) {
			throw new IllegalArgumentException("Digest length must be " + BYTES + " bytes for Hash, was " + length);
		}
		if (offset + length > alreadyHashedData.length) {
			throw new IllegalArgumentException(String.format(
				"Hash length must be at least %s for offset %s, but was %s", offset + length, offset, alreadyHashedData.length));
		}

		this.data = new byte[BYTES];
		System.arraycopy(alreadyHashedData, offset, this.data, 0, BYTES);
		this.hashCodeCached = calculateHashCode();
	}


	/**
	 * This does NOT perform any hashing, the byte array passed should be already hashed.
	 * <p>
	 * If you want to hash the data, use any of the static methods
	 * {@link Hash#hash256(byte[])} or {@link Hash#hash512(byte[])} instead.
	 *
	 * @param alreadyHashedDataAsHexString The data that has already been hashed, as a hexadecimal string
	 * @return a container for the already hashed data.
	 */
	public Hash(String alreadyHashedDataAsHexString) {
		Objects.requireNonNull(alreadyHashedDataAsHexString);
		if (alreadyHashedDataAsHexString.length() != (BYTES * 2)) {
			throw new IllegalArgumentException(String.format(
				"Digest length must be %s hex characters for Hash, was %s", BYTES * 2, alreadyHashedDataAsHexString.length()));
		}

		this.data = Bytes.fromHexString(alreadyHashedDataAsHexString);
		this.hashCodeCached = calculateHashCode();
	}

	// Required for EqualsVerifier.withCachedHashCode
	private int calculateHashCode() {
		return Arrays.hashCode(this.data);
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

	public EUID euid() {
		return idCached.get();
	}

	public byte getFirstByte() {
		return data[0];
	}

	@Override
	public int compareTo(Hash object) {
		return COMPARATOR.compare(this.data, object.data);
	}

	@Override
	public String toString() {
		return Bytes.toHexString(this.data);
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (o instanceof Hash) {
			Hash other = (Hash) o;
			 // Need to do full byte area comparison otherwise, susceptible to birthday attack
			return Arrays.equals(this.data, other.data);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return hashCodeCached;
	}

	private EUID computeId() {
		return new EUID(data, 0);
	}
}
