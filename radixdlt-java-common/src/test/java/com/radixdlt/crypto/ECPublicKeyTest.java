package com.radixdlt.crypto;

import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.utils.Bytes;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.Test;

public class ECPublicKeyTest {
	@Test
	public void equalsContract() throws PublicKeyException {
		final var p1 = ECKeyPair.generateNew().getPublicKey().getEcPoint();
		final var p2 = ECKeyPair.generateNew().getPublicKey().getEcPoint();
		var key = Bytes.fromBase64String("AtuRjZPGw0b0BIYx46e0iKCaFU5EPnPx7/wLk6Vcursg");
		ECPublicKey pk = ECPublicKey.fromBytes(key);
		EqualsVerifier.forClass(ECPublicKey.class)
			.withNonnullFields("ecPoint", "uncompressedBytes")
			.withIgnoredFields("uid", "ecPoint") // cached value
			.withPrefabValues(ECPoint.class, p1, p2)
			.withCachedHashCode("hashCode", "computeHashCode", pk)
			.verify();
	}
}
