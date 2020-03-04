package com.radixdlt.crypto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ECPublicKeyTest {
	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ECPublicKey.class)
				.withIgnoredFields("uid") // hash of public key bytes.
				.verify();
	}
}
