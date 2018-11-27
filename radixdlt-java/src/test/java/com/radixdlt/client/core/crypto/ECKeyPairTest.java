package com.radixdlt.client.core.crypto;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ECKeyPairTest {

    @Test
    public void decrypt_bad_encrypted_data_with_good_encrypted_private_key__should_throw_CryptoException() {
        ECKeyPair keyPair = ECKeyPairGenerator.newInstance().generateKeyPair();
        ECKeyPair privateKey = ECKeyPairGenerator.newInstance().generateKeyPair();

        EncryptedPrivateKey encryptedPrivateKey = privateKey.encryptPrivateKey(keyPair.getPublicKey());

        assertThatThrownBy(() -> keyPair.decrypt(new byte[]{0}, encryptedPrivateKey))
                .isInstanceOf(CryptoException.class);
    }

}
