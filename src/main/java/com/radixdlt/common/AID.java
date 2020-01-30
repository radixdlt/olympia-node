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

package com.radixdlt.common;

import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.crypto.Hash;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.Longs;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

/**
 * An Atom ID, made up of 192 bits of truncated hash and 64 bits of a selected shard.
 * The Atom ID is used so that Atoms can be located using just their hid.
 */
public final class AID implements Comparable<AID> {
	public static final int BYTES = 32;
	static final int HASH_BYTES = 24;
	static final int SHARD_BYTES = 8;

	public static final AID ZERO = new AID(new byte[BYTES]);

	private final byte[] value;

	private AID(byte[] bytes) {
		this.value = Objects.requireNonNull(bytes, "bytes is required");
		if (bytes.length != BYTES) {
			throw new IllegalArgumentException(String.format(
				"Bytes length must be %d but is %d",
				BYTES, bytes.length)
			);
		}
	}

	/**
	 * Gets the lowest 4 bytes of this AID as a long.
	 */
	public long getLow() {
		return Longs.fromByteArray(this.value);
	}

	/**
	 * Gets the shard encoded in this AID.
	 */
	public long getShard() {
		return Longs.fromByteArray(this.value, HASH_BYTES);
	}

	/**
	 * Checks whether this AID is zero.
	 */
	public boolean isZero() {
		for (byte aByte : value) {
			if (aByte != 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Copies this AID to a byte array with some offset.
	 * Note that the array must fit the offset + AID.BYTES.
	 * @param array The array
	 * @param offset The offset into that array
	 */
	public void copyTo(byte[] array, int offset) {
		Objects.requireNonNull(array, "array is required");
		if (array.length - offset < BYTES) {
			throw new IllegalArgumentException(String.format(
				"Array must be bigger than offset + %d but was %d",
				BYTES, array.length)
			);
		}

		System.arraycopy(this.value, 0, array, offset, BYTES);
	}

	@Override
	public String toString() {
		return Bytes.toHexString(this.value);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AID)) {
			return false;
		}

		return Arrays.equals(this.value, ((AID) o).value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(value);
	}

	/**
	 * Gets the underlying bytes of this AID.
	 * Note that this is NOT a copy and is the actual underlying byte array.
	 */
	public byte[] getBytes() {
		return this.value;
	}

	/**
	 * Create an AID from a hash and a set of shards
	 * The AID will contain 192 first bits of shard + 64 bits of selected shard
	 * @param hash The hash
	 * @param shards The shards
	 * @return The AID
	 */
	public static AID from(Hash hash, Set<Long> shards) {
		Objects.requireNonNull(hash, "hash is required");
		Objects.requireNonNull(shards, "shards is required");
		if (shards.isEmpty()) {
			throw new IllegalArgumentException("Shards cannot be empty");
		}

		// select the shard indexed by the first hash byte
		int selectedShardIndex = (hash.getFirstByte() & 0xff) % shards.size();
		long selectedShard = shards.stream().sorted(Long::compareUnsigned).skip(selectedShardIndex).findFirst()
			.orElseThrow(() -> new IllegalStateException("Missing"));
		byte[] bytes = new byte[BYTES];
		hash.copyTo(bytes, 0, HASH_BYTES);
		Longs.copyTo(selectedShard, bytes, HASH_BYTES);

		return new AID(bytes);
	}

	/**
	 * Create an AID from its bytes
	 * @param bytes The bytes (must be of length AID.BYTES)
	 * @return An AID with those bytes
	 */
	public static AID from(byte[] bytes) {
		Objects.requireNonNull(bytes, "bytes is required");
		if (bytes.length != BYTES) {
			throw new IllegalArgumentException(String.format(
				"Bytes length must be %d but is %d",
				BYTES, bytes.length)
			);
		}

		return new AID(bytes.clone());
	}

	/**
	 * Create an AID from a portion of a byte array
	 * @param bytes The bytes (must be of length AID.BYTES)
	 * @param offset The offset into the bytes array
	 * @return An AID with those bytes
	 */
	public static AID from(byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "bytes is required");
		if (offset < 0) {
			throw new IllegalArgumentException("Offset must be >= 0: " + offset);
		}
		if (offset + BYTES > bytes.length) {
			throw new IllegalArgumentException(String.format(
				"Bytes length must be %d but is %d",
				offset + BYTES, bytes.length)
			);
		}

		return new AID(Arrays.copyOfRange(bytes, offset, offset + BYTES));
	}

	/**
	 * Create an AID from its hex bytes
	 * @param hexBytes The bytes in hex (must be of length AID.BYTES * 2)
	 * @return An AID with those bytes
	 */
	public static AID from(String hexBytes) {
		Objects.requireNonNull(hexBytes, "hexBytes is required");
		if (hexBytes.length() != BYTES * 2) {
			throw new IllegalArgumentException(String.format(
				"Hex bytes string length must be %d but is %d",
				BYTES * 2, hexBytes.length())
			);
		}

		return new AID(Bytes.fromHexString(hexBytes));
	}

	@Override
	public int compareTo(AID o) {
		return lexicalComparator().compare(this, o);
	}

	private static final class LexicalComparatorHolder {
		private static final Comparator<byte[]> BYTES_COMPARATOR = UnsignedBytes.lexicographicalComparator();
		private static final Comparator<AID> INSTANCE = (o1, o2) -> BYTES_COMPARATOR.compare(o1.value, o2.value);
	}

	/**
	 * Get a lexical comparator for this type.
	 */
	public static Comparator<AID> lexicalComparator() {
		return LexicalComparatorHolder.INSTANCE;
	}
}
