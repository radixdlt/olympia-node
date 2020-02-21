package com.radixdlt.crypto;

import com.radixdlt.serialization.SerializerId2;

@SerializerId2("crypto.ecdsa_signatures")
public class ECDSASignatures extends SignaturesImpl<ECDSASignature> {

    public ECDSASignatures() {
        super(ECDSASignature.class);
    }

    public ECDSASignatures(ECPublicKey publicKey, ECDSASignature signature) {
        super(ECDSASignature.class, publicKey, signature);
    }

    @Override
    public SignatureScheme signatureScheme() {
        return SignatureScheme.ECDSA;
    }
}
