package com.radixdlt.client.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Hash {

	static {
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	private Hash() {
	}

	private static byte[] hash(String algorithm, byte[] data, int offset, int len) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance(algorithm, "BC");
			synchronized (messageDigest) {
				messageDigest.update(data, offset, len);
				return messageDigest.digest();
			}
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e.getMessage());
		} catch (NoSuchProviderException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	public static byte[] sha512(byte[] data) {
		return hash("SHA-512", data, 0, data.length);
	}

	public static byte[] sha256(byte[] data) {
		return sha256(data, 0, data.length);
	}

	// Hashes the specified byte array using SHA-256
	public static byte[] sha256(byte[] data, int offset, int len) {
		return hash("SHA-256", data, offset, len);
	}
}
