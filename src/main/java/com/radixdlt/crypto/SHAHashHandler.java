package com.radixdlt.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class SHAHashHandler implements HashHandler {
	// Note that default provide around 20-25% faster than Bouncy Castle.
	// See jmh/org.radix.benchmark.HashBenchmark
	private final ThreadLocal<MessageDigest> hash256DigesterInner = ThreadLocal.withInitial(() -> getDigester("SHA-256"));
	private final ThreadLocal<MessageDigest> hash256DigesterOuter = ThreadLocal.withInitial(() -> getDigester("SHA-256"));
	private final ThreadLocal<MessageDigest> hash512Digester = ThreadLocal.withInitial(() -> getDigester("SHA-512"));

	SHAHashHandler() {
	}

	void reset() {
		hash256DigesterInner.remove();
		hash256DigesterOuter.remove();
		hash512Digester.remove();
	}

	@Override
	public byte[] hash256(byte[] data) {
		return hash256(data, 0, data.length);
	}

	@Override
	public byte[] hash256(byte[] data, int offset, int length) {
		// Here we use SHA-256(SHA-512(data)) to avoid length-extension attack
		final MessageDigest hash256DigesterOuterLocal = hash256DigesterOuter.get();
		final MessageDigest hash256DigesterInnerLocal = hash256DigesterInner.get();
		hash256DigesterOuterLocal.reset();
		hash256DigesterInnerLocal.reset();
		hash256DigesterInnerLocal.update(data, offset, length);
		return hash256DigesterOuterLocal.digest(hash256DigesterInnerLocal.digest());
	}

	@Override
	public byte[] hash256(byte[] data0, byte[] data1) {
		// Here we use SHA-256(SHA-512(data0 || data1)) to avoid length-extension attack
		final MessageDigest hash256DigesterInnerLocal = hash256DigesterInner.get();
		final MessageDigest hash256DigesterOuterLocal = hash256DigesterOuter.get();
		hash256DigesterInnerLocal.reset();
		hash256DigesterOuterLocal.reset();
		hash256DigesterInnerLocal.update(data0);
		return hash256DigesterOuterLocal.digest(hash256DigesterInnerLocal.digest(data1));
	}

	@Override
	public byte[] hash512(byte[] data, int offset, int length) {
		// Here we use SHA-512(SHA-512(data0 || data1)) to avoid length-extension attack
		final MessageDigest hash512DigesterLocal = hash512Digester.get();
		hash512DigesterLocal.reset();
		hash512DigesterLocal.update(data, offset, length);
		return hash512DigesterLocal.digest(hash512DigesterLocal.digest());
	}

	private static MessageDigest getDigester(String algorithm) {
		try {
			return  MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException("No such algorithm: " + algorithm, e);
		}
	}
}
