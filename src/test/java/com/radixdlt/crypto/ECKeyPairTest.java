package com.radixdlt.crypto;

import java.nio.charset.StandardCharsets;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ECKeyPairTest {
	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void checkKeyIntegrity() throws CryptoException {
		final int ITERATIONS = 5000;

		for (int i = 0; i < ITERATIONS; i++) {
			ECKeyPair key = new ECKeyPair();

			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			key = new ECKeyPair(priv);

			Assert.assertArrayEquals(priv, key.getPrivateKey());
			Assert.assertArrayEquals(pub, key.getPublicKey().getBytes());
		}
	}

	@Test
	public void signAndVerify() throws CryptoException {
		final int ITERATIONS = 2000;
		String helloWorld = "Hello World";

		for (int i = 0; i < ITERATIONS; i++) {
			ECKeyPair key = new ECKeyPair();
			byte[] priv = key.getPrivateKey();
			byte[] pub = key.getPublicKey().getBytes();

			ECKeyPair keyPair = new ECKeyPair(priv);
			ECSignature signature = keyPair.sign(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)));

			ECPublicKey pubkey = new ECPublicKey(pub);
			Assert.assertTrue(pubkey.verify(Hash.hash256(helloWorld.getBytes(StandardCharsets.UTF_8)), signature));
		}
	}

	@Test
	public void encryptAndDecrypt() throws CryptoException {
		final int ITERATIONS = 1000;
		String helloWorld = "Hello World";

		for (int i = 0; i < ITERATIONS; ++i) {
			ECKeyPair key = new ECKeyPair();
			byte[] priv = key.getPrivateKey();


			byte[] encrypted = key.getPublicKey().encrypt(helloWorld.getBytes(StandardCharsets.UTF_8));

			ECKeyPair newkey = new ECKeyPair(priv);
			Assert.assertArrayEquals(helloWorld.getBytes(StandardCharsets.UTF_8), newkey.decrypt(encrypted));
		}
	}

	@Test
	public void checkKeyPairEquals() {
		EqualsVerifier.forClass(ECKeyPair.class)
			.withIgnoredFields("publicKey") // Computed
			.verify();
	}

	@Test
	public void checkPublicKeyEquals() {
		EqualsVerifier.forClass(ECPublicKey.class)
			.withIgnoredFields("uid") // Computed and cached
			.verify();
	}
}