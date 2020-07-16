package com.radixdlt.crypto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

public class ECPublicKeyTest {
	@Test
	public void equalsContract() throws CryptoException {
		ECPublicKey pk = ECPublicKey.fromBase64("AtuRjZPGw0b0BIYx46e0iKCaFU5EPnPx7/wLk6Vcursg");
		EqualsVerifier.forClass(ECPublicKey.class)
			.withIgnoredFields("uid") // cached value
			.withCachedHashCode("hashCode", "computeHashCode", pk)
			.verify();
	}
}
