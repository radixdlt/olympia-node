package com.radixdlt.client.core.crypto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class ECKeyPairTest {
	@Test
	public void decryptBadEncryptedDataWithGoodEncryptedPrivateKeyShouldThrowCryptoException() {
		ECKeyPair keyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
		ECKeyPair privateKey = ECKeyPairGenerator.newInstance().generateKeyPair();

		EncryptedPrivateKey encryptedPrivateKey = privateKey.encryptPrivateKey(keyPair.getPublicKey());

		assertThatThrownBy(() -> keyPair.decrypt(new byte[] {0}, encryptedPrivateKey))
			.isInstanceOf(CryptoException.class);
	}
}