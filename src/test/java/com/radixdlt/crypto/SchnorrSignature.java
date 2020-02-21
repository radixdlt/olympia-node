package com.radixdlt.crypto;

public class SchnorrSignature implements Signature {
    @Override
    public SignatureScheme signatureScheme() {
        return SignatureScheme.SCHNORR;
    }
}
