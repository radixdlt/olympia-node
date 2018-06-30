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
	public void encryptionTest() throws MacMismatchException {
		String testPhrase = "Hello World";
		ECKeyPair ecKeyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
		byte[] encrypted = ecKeyPair.getPublicKey().encrypt(testPhrase.getBytes());
		assertTrue(encrypted.length > 0);
		byte[] decrypted = ecKeyPair.decrypt(encrypted);
		assertEquals(testPhrase, new String(decrypted));
	}
}