package com.radixdlt.client.core.crypto;

import com.radixdlt.client.core.atoms.Atom;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class RadixECKeyPairsTest {

    @BeforeClass
    public static void beforeSuite() {
        // Ensure the BouncyCastle providers are loaded into memory
        // (because BouncyCastle SHA-256 is used in "seed" tests).
        ECKeyPairGenerator.install();
    }

    @Test
    public void when_generating_two_default_key_pairs__they_should_have_different_private_keys() {
        byte[] privateKey1 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPair()
                .getPrivateKey();

        byte[] privateKey2 = ECKeyPairGenerator
                .newInstance()
                .generateKeyPair()
                .getPrivateKey();

        assertThat(privateKey1, not(equalTo(privateKey2)));
    }

    @Test
    public void when_generating_two_key_pairs_from_same_seed__they_should_have_same_private_keys() {
        byte[] seed = "seed".getBytes();
        byte[] privateKey1 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        byte[] privateKey2 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed)
                .getPrivateKey();

        assertThat(privateKey1, equalTo(privateKey2));
    }

    @Test
    public void when_signing_an_atom_with_a_seeded_key_pair__another_key_pair_from_same_seed_can_verify_the_signature() {
        byte[] seed = "seed".getBytes();
        ECKeyPair keyPair1 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed);

        ECKeyPair keyPair2 = RadixECKeyPairs
                .newInstance()
                .generateKeyPairFromSeed(seed);

        Atom atom1 = new Atom(Collections.emptyList());
        Atom atom2 = new Atom(Collections.emptyList());
        ECSignature signature1 = keyPair1.sign(atom1.toDson());
        ECSignature signature2 = keyPair2.sign(atom2.toDson());

        // Assert that KeyPair1 can be used to verify the signature of Atom2
        assertTrue(keyPair1.getPublicKey().verify(atom2.toDson(), signature2));

        // Assert that KeyPair2 can be used to verify the signature of Atom1
        assertTrue(keyPair2.getPublicKey().verify(atom1.toDson(), signature1));
    }
}
