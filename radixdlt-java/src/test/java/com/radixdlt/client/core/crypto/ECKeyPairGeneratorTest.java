package com.radixdlt.client.core.crypto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ECKeyPairGeneratorTest {
	@Test
	public void generateKeyPair() {
		assertNotNull(ECKeyPairGenerator.generateKeyPair());
	}

	@Test
	public void test256bits() {
		for (int i=0; i<1000; i++) {
			ECKeyPair ecKeyPair = ECKeyPairGenerator.generateKeyPair();
			assertEquals(32, ecKeyPair.getPrivateKey().length);
		}
	}

}