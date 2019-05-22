package org.radix.common.ID;

import com.radixdlt.client.core.atoms.RadixHash;
import org.bouncycastle.util.encoders.Hex;
import org.radix.utils.primitives.Longs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/**
 * An Atom ID, made up of 192 bits of truncated hash and 64 bits of a selected shard.
 * The Atom ID is used so that Atoms can be located using just their hid.
 * The lowest shard is chosen for no particular reason, it only needs to be deterministic between implementations.
 */
public final class AID implements Comparable<AID> {
	public static final int BYTES = 32;
	static final int HASH_BYTES = 24;
	static final int SHARD_BYTES = 8;

	public static final AID ZERO = new AID(new byte[BYTES]);

	private byte[] bytes;

	private AID() {
	}

	private AID(byte[] bytes) {
		this.bytes = Objects.requireNonNull(bytes, "bytes is required");
		if (bytes.length != BYTES) {
			throw new IllegalArgumentException(String.format(
				"Bytes length must be %d but is %d",
				BYTES, bytes.length)
			);
		}
	}

	/**
	 * Get the lowest 4 bytes of this AID as a long
	 */
	public long getLow() {
		return Longs.fromByteArray(this.bytes);
	}

	/**
	 * Get the shard encoded in this AID
	 */
	public long getShard() { return Longs.fromByteArray(this.bytes, HASH_BYTES); };

	/**
	 * Copy this AID to a byte array with some offset
	 * Note that the array must fit the offset + AID.BYTES
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

		System.arraycopy(this.bytes, 0, array, offset, BYTES);
	}

	/**
	 * Copy this AID to an output stream
	 * @param out The output stream
	 */
	public void copyTo(OutputStream out) throws IOException {
		Objects.requireNonNull(out, "out is required");
		out.write(bytes);
	}

	@Override
	public String toString() {
		return Hex.toHexString(this.bytes);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AID aid = (AID) o;
		return Arrays.equals(this.bytes, aid.bytes);
	}

	@Override
	public int compareTo(AID other) {
		for (int i = 0; i < BYTES; i++) {
			int diff = other.bytes[i] - this.bytes[i];
			if (diff != 0) {
				return diff;
			}
		}

		return 0;
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	public byte[] toByteArray() {
		return bytes.clone();
	}

	/**
	 * Create an AID from a hash and a set of shards
	 * The AID will contain 192 first bits of shard + 64 bits of selected shard
	 * @param hash The hash
	 * @param shards The shards
	 * @return The AID
	 */
	public static AID from(RadixHash hash, Set<Long> shards) {
		Objects.requireNonNull(hash, "hash is required");
		Objects.requireNonNull(shards, "shards is required");
		if (shards.isEmpty()) {
			throw new IllegalArgumentException("Shards cannot be empty");
		}

		// select the shard indexed by the first hash byte
		int selectedShardIndex = (hash.getFirstByte() - Byte.MIN_VALUE) % shards.size();
		long selectedShard = shards.toArray(new Long[0])[selectedShardIndex];
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

		return new AID(bytes);
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

		return new AID(Hex.decode(hexBytes));
	}
}
