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

package org.radix.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.util.Arrays;
import org.radix.common.ID.EUID;
import org.radix.utils.primitives.Bytes;

public class Hash implements Comparable<Hash> {

	public static final int BYTES = 32;
	public static final Hash ZERO_HASH = new Hash(new byte[BYTES]);

	public static Hash random() {
		byte[] randomBytes = new byte[BYTES];

		new SecureRandom().nextBytes(randomBytes);

		return new Hash(Hash.hash(randomBytes));
	}

	public static byte[] hash(byte[] data) {
		return hash(data, 0, data.length);
	}

	public static byte[] hash(byte[] data, int offset, int len) {
		return hash("SHA-256", data, offset, len);
	}

	public static byte[] hash(String algorithm, byte[] data) {
		return hash(algorithm, data, 0, data.length);
	}

	private static ConcurrentHashMap<String, MessageDigest> digesters = new ConcurrentHashMap<>();

	public static byte[] hash(String algorithm, byte[] data, int offset, int len) {
		MessageDigest digester = digesters.computeIfAbsent(algorithm, Hash::getDigester);
		synchronized (digester) {
			digester.reset();
			digester.update(data, offset, len);
			return digester.digest(digester.digest());
		}
	}

	private static MessageDigest getDigester(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
		}
	}

	private final byte[] bytes;
	private EUID id;

	public Hash(byte[] hash) {
		this(hash, 0, hash.length);
	}

	public Hash(byte[] hash, int offset, int length) {
		if (length != BYTES) {
			throw new IllegalArgumentException("Digest length must be " + BYTES + " bytes for Hash");
		}

		this.bytes = new byte[BYTES];
		System.arraycopy(hash, offset, this.bytes, 0, BYTES);
	}

	public Hash(String hex) {
		if (hex.length() != (BYTES * 2)) {
			throw new IllegalArgumentException("Digest length must be 64 hex characters for Hash");
		}

		this.bytes = Bytes.fromHexString(hex);
	}

	public byte[] toByteArray() {
		return bytes.clone();
	}

	@Override
	public int compareTo(Hash object) {
		return this.toString().compareTo(object.toString());
	}

	@Override
	public String toString() {
		return Bytes.toHexString(toByteArray());
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		if (o == this) {
			return true;
		}

		return (o instanceof Hash && Arrays.areEqual(((Hash) o).bytes, this.bytes));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.bytes);
	}

	public EUID getID() {
		if (id == null) {
			id = new EUID(Arrays.copyOfRange(bytes, 0, EUID.BYTES));
		}

		return id;
	}

	protected byte[] getBytes() {
		return bytes;
	}
}
