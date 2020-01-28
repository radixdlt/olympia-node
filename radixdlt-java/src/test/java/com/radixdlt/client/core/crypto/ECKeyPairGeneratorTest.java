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

package com.radixdlt.client.core.crypto;

import java.security.Provider;
import java.security.SecureRandomSpi;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ECKeyPairGeneratorTest {
	public static class FakeSha1Provider extends Provider {
		FakeSha1Provider() {
			super("FakeSha1Provider", 1.0, null);
			put("SecureRandom.SHA1PRNG", FakeAlgorithm.class.getName());
		}
	}

	public static class FakeAlgorithm extends SecureRandomSpi {
		private final AtomicInteger cur = new AtomicInteger(0);

		@Override
		protected void engineSetSeed(byte[] seed) {
			for (int i = 0; i < seed.length; i++) {
				// Deterministic result for a given length
				seed[i] = (byte) (cur.getAndIncrement());
			}
		}

		@Override
		protected void engineNextBytes(byte[] bytes) {
			for (int i = 0; i < bytes.length; i++) {
				bytes[i] = (byte) (cur.getAndIncrement());
			}
		}

		@Override
		protected byte[] engineGenerateSeed(int numBytes) {
			byte[] bytes = new byte[numBytes];
			for (int i = 0; i < numBytes; i++) {
				bytes[i] = (byte) (cur.getAndIncrement());
			}
			return bytes;
		}
	}

	@BeforeClass
	public static void init() {
		Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		// Give fake provider highest priority
		Security.insertProviderAt(new FakeSha1Provider(), 1);
		Security.insertProviderAt(new BouncyCastleProvider(), 2);
	}

	@AfterClass
	public static void after() {
		Security.removeProvider("FakeSha1Provider");
		Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
		Security.insertProviderAt(new BouncyCastleProvider(), 1);
	}

	@Test
	public void generateKeyPair() {
		assertNotNull(ECKeyPairGenerator.newInstance().generateKeyPair());
	}

	@Test
	public void test256bits() {
		for (int i = 0; i < 1000; i++) {
			ECKeyPair ecKeyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
			assertEquals(32, ecKeyPair.getPrivateKey().length);
		}
	}

	@Test
	public void encryptionTest() throws CryptoException {
		String testPhrase = "Hello World";
		ECKeyPair ecKeyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
		byte[] encrypted = ecKeyPair.getPublicKey().encrypt(testPhrase.getBytes());
		assertTrue(encrypted.length > 0);
		byte[] decrypted = ecKeyPair.decrypt(encrypted);
		assertEquals(testPhrase, new String(decrypted));
	}
}