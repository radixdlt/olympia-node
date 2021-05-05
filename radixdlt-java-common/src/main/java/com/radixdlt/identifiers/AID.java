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

package com.radixdlt.identifiers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.hash.HashCode;
import com.google.common.primitives.UnsignedBytes;
import com.radixdlt.utils.Bytes;
import com.radixdlt.utils.functional.Result;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import static com.radixdlt.identifiers.CommonErrors.AID_IS_NULL;
import static com.radixdlt.identifiers.CommonErrors.INVALID_LENGTH;
import static com.radixdlt.identifiers.CommonErrors.UNABLE_TO_DECODE;
import static com.radixdlt.utils.functional.Result.fromOptional;

import static java.util.Optional.ofNullable;

/**
 * An Atom ID, made up of 256 bits of a hash.
 * The Atom ID is used so that Atoms can be located using just their hid.
 */
public final class AID implements Comparable<AID> {
	static final int HASH_BYTES = 32;
	public static final int BYTES = HASH_BYTES;

	public static final AID ZERO = new AID(new byte[BYTES]);

	private final byte[] value;

	private AID(byte[] bytes) {
		assert (bytes != null && bytes.length == HASH_BYTES);
		this.value = bytes;
	}

	/**
	 * Copies this AID to a byte array with some offset.
	 * Note that the array must fit the offset + AID.BYTES.
	 *
	 * @param array The array
	 * @param offset The offset into that array
	 */
	public void copyTo(byte[] array, int offset) {
		Objects.requireNonNull(array, "array is required");
		if (array.length - offset < BYTES) {
			throw new IllegalArgumentException(
				String.format("Array must be bigger than offset + %d but was %d", BYTES, array.length)
			);
		}
		System.arraycopy(this.value, 0, array, offset, BYTES);
	}

	public HashCode asHashCode() {
		return HashCode.fromBytes(value);
	}

	@JsonValue
	public String toJson() {
		return Bytes.toHexString(this.value);
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
		if (hashCode() != o.hashCode()) {
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
	 * Create an AID from its bytes
	 *
	 * @param bytes The bytes (must be of length AID.BYTES)
	 *
	 * @return An AID with those bytes
	 */
	public static AID from(byte[] bytes) {
		return from(bytes, 0);
	}

	/**
	 * Create an AID from a portion of a byte array
	 *
	 * @param bytes The bytes (must be of length AID.BYTES)
	 * @param offset The offset into the bytes array
	 *
	 * @return An AID with those bytes
	 */
	public static AID from(byte[] bytes, int offset) {
		Objects.requireNonNull(bytes, "AID decoding error: input must not be null");
		if (offset < 0) {
			throw new IllegalArgumentException("AID decoding error: offset must be >= 0: " + offset);
		}
		if (offset + BYTES > bytes.length) {
			throw new IllegalArgumentException(
				String.format("AID decoding error: length must be %d but is %d", offset + BYTES, bytes.length)
			);
		}
		return new AID(Arrays.copyOfRange(bytes, offset, offset + BYTES));
	}

	/**
	 * Create an AID from its hex bytes
	 *
	 * @param hexBytes The bytes in hex (must be of length AID.BYTES * 2)
	 *
	 * @return An AID with those bytes
	 */
	@JsonCreator
	public static AID from(String hexBytes) {
		Objects.requireNonNull(hexBytes, "hexBytes is required");
		if (hexBytes.length() != BYTES * 2) {
			throw new IllegalArgumentException(
				String.format("Hex bytes string length must be %d but is %d", BYTES * 2, hexBytes.length())
			);
		}

		return new AID(Bytes.fromHexString(hexBytes));
	}

	/**
	 * Functional style friendly version of {@link #from(String)}.
	 *
	 * @param input The string to parse
	 *
	 * @return Success {@link Result} if value can be successfully parsed and failure {@link Result} otherwise.
	 */
	public static Result<AID> fromString(String input) {
		return fromOptional(AID_IS_NULL, ofNullable(input))
			.filter(bytes -> bytes.length() == BYTES * 2, INVALID_LENGTH)
			.map(Bytes::fromHexString)
			.filter(bytes -> bytes.length == HASH_BYTES, INVALID_LENGTH)
			.map(AID::new);
	}

	/**
	 * Create an AID from bytes. Unlike {@link #from(byte[])} this method does not throw an exception.
	 *
	 * @param bytes The bytes (must be of length AID.BYTES)
	 *
	 * @return Success result in case of successful conversion and failure result in case of error.
	 */
	public static Result<AID> fromBytes(byte[] bytes) {
		return Result.wrap(UNABLE_TO_DECODE, () -> from(bytes));
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
