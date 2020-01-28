/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

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
		this.hash = Objects.requireNonNull(hash, "hash is required");
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(hash, hash.length);
	}

	public byte getFirstByte() {
		assert hash.length > 0;
		return hash[0];
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
