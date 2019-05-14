package org.radix.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Objects;
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
			return MessageDigest.getInstance(algorithm, "BC");
		} catch (NoSuchProviderException e) {
			throw new IllegalArgumentException("No such provider for: " + algorithm, e);
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
