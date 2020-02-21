package com.radixdlt.crypto;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class SignaturesTest {

    @Test
    public void verify_that_default_signature_scheme_is_ecdsa() {
        Signatures emptySignatures = Signatures.defaultEmptySignatures();

        assertEquals(emptySignatures.signatureScheme(), SignatureScheme.ECDSA);
        assertTrue(emptySignatures.isEmpty());
        ECDSASignature mockSignature = mock(ECDSASignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);

        Signatures nonEmptySignatures = emptySignatures.concatenate(publicKey, mockSignature);
        assertEquals(nonEmptySignatures.keyToSignatures().size(), 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verify_that_we_crash_if_we_try_to_concatenate_signatures_with_a_signature_of_incompatible_type() {
        Signatures emptySignatures = Signatures.defaultEmptySignatures();
        assertEquals(emptySignatures.signatureScheme(), SignatureScheme.ECDSA);
        assertTrue(emptySignatures.isEmpty());
        SchnorrSignature schnorr = mock(SchnorrSignature.class);
        ECPublicKey publicKey = mock(ECPublicKey.class);
        emptySignatures.concatenate(publicKey, schnorr);
    }

}
