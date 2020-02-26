package com.radixdlt.crypto;

import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class SignaturesTest {

    @Test
    public void verify_that_default_signature_scheme_is_ecdsa() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();

        assertEquals(emptySignatures.signatureScheme(), SignatureScheme.ECDSA);
        assertTrue(emptySignatures.isEmpty());
        ECDSASignature mockSignature = mock(ECDSASignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);

        Signatures nonEmptySignatures = emptySignatures.concatenate(publicKey, mockSignature);
        assertEquals(nonEmptySignatures.keyToSignatures().size(), 1);
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void test_that_we_can_bulk_verify_signatures_verify_all_two() throws CryptoException {
        test_that_we_can_bulk_verify_signatures(
                2,
                true,
                2,
                0
        );
    }

    @Test(expected = Test.None.class /* no exception expected */)
    public void test_that_we_can_bulk_verify_signatures_verify_at_least_N_of_M() throws CryptoException {
        for (int valid = 0; valid < 5; valid++) {
            for (int invalid = 0; invalid < 5; invalid++) {
                for (int threshold = 0; threshold <= (valid + invalid); threshold++) {

                    boolean isExpectedToMeetThreshold = valid >= threshold;
                    test_that_we_can_bulk_verify_signatures(
                            threshold,
                            isExpectedToMeetThreshold,
                            valid,
                            invalid
                    );
                }

            }
        }
    }

    private void test_that_we_can_bulk_verify_signatures(
            int thresholdNumberOfValidSignatures,
            boolean isExpectedToMeetThreshold,
            int numberOfValidSignaturesToCreate,
            int numberOfInvalidSignaturesToCreate
    ) throws CryptoException {
        if (thresholdNumberOfValidSignatures > (numberOfValidSignaturesToCreate + numberOfInvalidSignaturesToCreate)) {
            throw new IllegalArgumentException(
                    "The sum of valid + invalid signatures should >= 'thresholdNumberOfValidSignatures'"
            );
        }
        Signatures signatures = DefaultSignatures.emptySignatures();
        Hash hashedMessage = new Hash(Hash.hash256("You must do what you feel is right of course".getBytes()));
        for (int i = 0; i < numberOfValidSignaturesToCreate + numberOfInvalidSignaturesToCreate; i++) {
            ECKeyPair keyPair = new ECKeyPair();
            assertNotNull(keyPair);
            Signature signature = null;
            boolean shouldSignatureBeValid = i >= numberOfInvalidSignaturesToCreate;
            if (shouldSignatureBeValid) {
                signature = keyPair.sign(hashedMessage);
            } else {
                signature = new ECDSASignature(BigInteger.ONE, BigInteger.ONE);
            }
            assertNotNull(signature);

            signatures = signatures.concatenate(keyPair.getPublicKey(), signature);
        }

        assertEquals((numberOfInvalidSignaturesToCreate + numberOfValidSignaturesToCreate), signatures.keyToSignatures().size());
        boolean doesSignatureMeetValidityThreshold = signatures.hasSignedMessage(hashedMessage, thresholdNumberOfValidSignatures);
        assertEquals(isExpectedToMeetThreshold, doesSignatureMeetValidityThreshold);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verify_that_we_crash_if_we_try_to_concatenate_signatures_with_a_signature_of_incompatible_type_schnorr_to_ecdsa() {
        Signatures emptySignatures = DefaultSignatures.emptySignatures();
        assertEquals(emptySignatures.signatureScheme(), SignatureScheme.ECDSA);
        assertTrue(emptySignatures.isEmpty());
        SchnorrSignature schnorr = mock(SchnorrSignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);
        emptySignatures.concatenate(publicKey, schnorr);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verify_that_we_crash_if_we_try_to_concatenate_signatures_with_a_signature_of_incompatible_type_ecdsa_to_schnorr() {
        Signatures emptySignatures = new SignaturesImpl<>(SchnorrSignature.class);
        assertTrue(emptySignatures.isEmpty());
        ECDSASignature ecdsaSignature = mock(ECDSASignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);
        emptySignatures.concatenate(publicKey, ecdsaSignature);
    }

}
