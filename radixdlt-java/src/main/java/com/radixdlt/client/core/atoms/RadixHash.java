package com.radixdlt.client.core.atoms;

import com.radixdlt.client.core.address.EUID;
import com.radixdlt.client.core.crypto.ECPublicKey;
import com.radixdlt.client.core.crypto.ECSignature;
import com.radixdlt.client.core.util.Hash;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class RadixHash {
	private static final int HASH_MAX_SIZE = 12;
	private final byte[] hash;

	private RadixHash(byte[] hash) {
		this.hash = hash;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(hash, hash.length);
	}

	public EUID toEUID() {
		return new EUID(Arrays.copyOfRange(hash, 0, HASH_MAX_SIZE));
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
		return new RadixHash(Hash.SHA256(Hash.SHA256(data)));
	}

	public static RadixHash of(byte[] data, int offset, int length) {
		return new RadixHash(Hash.SHA256(Hash.SHA256(data, offset, length)));
	}

	public static RadixHash SHA512of(byte[] data) {
		return new RadixHash(Hash.SHA512(Hash.SHA512(data)));
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}

		if (!(o instanceof RadixHash)) {
			return false;
		}

		RadixHash other = (RadixHash)o;
		return Arrays.equals(hash, other.hash);
	}

	@Override
	public int hashCode() {
		return new BigInteger(hash).hashCode();
	}
}
