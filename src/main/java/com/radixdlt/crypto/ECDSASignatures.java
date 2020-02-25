package com.radixdlt.crypto;

import com.radixdlt.serialization.SerializerId2;

import java.util.Map;

@SerializerId2("crypto.ecdsa_signatures")
public class ECDSASignatures extends SignaturesImpl<ECDSASignature> {

    public ECDSASignatures() {
        super(ECDSASignature.class);
    }

    public ECDSASignatures(ECPublicKey publicKey, ECDSASignature signature) {
        super(ECDSASignature.class, publicKey, signature);
    }

    /**
     * Returns a new instance containing {@code keyToSignature}.
     * @param keyToSignature The map of {@link ECDSASignature}s and their corresponding {@link ECPublicKey}
     */
    public ECDSASignatures(Map<ECPublicKey, ECDSASignature> keyToSignature) {
        super(ECDSASignature.class, keyToSignature);
    }

    @Override
    public SignatureScheme signatureScheme() {
        return SignatureScheme.ECDSA;
    }
}
