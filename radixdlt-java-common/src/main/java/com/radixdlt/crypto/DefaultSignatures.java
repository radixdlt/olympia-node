package com.radixdlt.crypto;

public final class DefaultSignatures {

    private DefaultSignatures() {
        throw new IllegalStateException("Can't construct.");
    }

    private static final Signatures DEFAULT = new ECDSASignatures();

    /**
     * Returns an empty collection of {@link Signature}s, with the default {@link SignatureScheme} used.
     * @return an empty collection of {@link Signature}s, with the default {@link SignatureScheme} used.
     */
    public static Signatures emptySignatures() {
        return DEFAULT;
    }

    /**
     * Returns a collection of {@link Signature}s, with the default {@link SignatureScheme} used, containing the {@code signature},
     * produced by the signing key corresponding to the {@code publicKey}.
     * @param publicKey the {@link ECPublicKey} corresponding to the {@link Signing} key which was used to produce the {@code signature}.
     * @param signature the {@link Signature} produced by the {@link Signing} key corresponding to the {@code publicKey}.
     * @return an instance of the default {@link SignatureScheme} used, containing the {@code signature},
     * produced by the signing key corresponding to the {@code publicKey}.
     */
    public static Signatures single(ECPublicKey publicKey, Signature signature) {
        return emptySignatures().concatenate(publicKey, signature);
    }
}
