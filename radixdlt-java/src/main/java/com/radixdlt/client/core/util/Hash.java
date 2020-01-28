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

package com.radixdlt.client.core.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class Hash {

	static {
		if (AndroidUtil.isAndroidRuntime()) {
			Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		}
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	private Hash() {
	}

	private static byte[] hash(String algorithm, byte[] data, int offset, int len) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
			synchronized (messageDigest) {
				messageDigest.update(data, offset, len);
				return messageDigest.digest();
			}
		} catch (NoSuchAlgorithmException e) {
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
