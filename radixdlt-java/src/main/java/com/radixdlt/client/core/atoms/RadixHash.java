package com.radixdlt.client.core.atoms;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import org.bouncycastle.util.encoders.Base64;
import org.radix.common.ID.EUID;

import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.util.Hash;
import org.radix.utils.primitives.Bytes;

public final class RadixHash {
	private final byte[] hash;

	private RadixHash(byte[] hash) {
		this.hash = hash;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(hash, hash.length);
	}

	public void copyTo(byte[] array, int offset) {
		copyTo(array, offset, this.hash.length);
	}

	public void copyTo(byte[] array, int offset, int length) {
		Objects.requireNonNull(array, "array is required");
		if (array.length - offset < this.hash.length) {
			throw new IllegalArgumentException(String.format(
				"Array must be bigger than offset + %d but was %d",
				this.hash.length, array.length)
			);
		}

		System.arraycopy(this.hash, 0, array, offset, length);
	}

	public EUID toEUID() {
		return new EUID(Arrays.copyOfRange(hash, 0, EUID.BYTES));
	}

	public void putSelf(ByteBuffer byteBuffer) {
		byteBuffer.put(hash);
	}

	public boolean verifySelf(ECPublicKey publicKey, ECSignature signature) {
		return publicKey.verify(hash, signature);
	}

	public byte get(int index) {
		return hash[index];
	}

	public static RadixHash of(byte[] data) {
		return new RadixHash(Hash.sha256(Hash.sha256(data)));
	}

	public static RadixHash of(byte[] data, int offset, int length) {
		return new RadixHash(Hash.sha256(Hash.sha256(data, offset, length)));
	}

	public static RadixHash sha512of(byte[] data) {
		return new RadixHash(Hash.sha512(Hash.sha512(data)));
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof RadixHash)) {
			return false;
		}

		RadixHash other = (RadixHash) o;
		return Arrays.equals(hash, other.hash);
	}

	@Override
	public int hashCode() {
		// Set sign to positive to stop BigInteger interpreting high bit as sign
		return new BigInteger(1, hash).hashCode();
	}

	@Override
	public String toString() {
		return Base64.toBase64String(hash);
	}

	public String toHexString() {
		return Bytes.toHexString(hash);
	}
}
