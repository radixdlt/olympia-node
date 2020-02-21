package com.radixdlt.crypto;

import java.util.Map;

public interface Signatures {

    /**
     * Returns the {@link SignatureScheme} used to produce the signatures of this collection.
     * @return Returns the {@link SignatureScheme} used to produce the signatures of this collection.
     */
    SignatureScheme signatureScheme();

    /**
     * Returns the type of {@link Signature} produced by the {@code signatureScheme}.
     * @return Returns the type of {@link Signature} produced by the {@code signatureScheme}.
     */
    Class<? extends Signature> signatureType();

    /**
     * Returns the map from {@link ECPublicKey} to {@link Signature} produced by that key.
     * @return Returns the map from {@link ECPublicKey} to {@link Signature} produced by that key.
     */
    Map<ECPublicKey, Signature> keyToSignatures();

    /**
     * Returns a new instance of {@link Signatures}, concatenated with the {@code signature},
     * produced by the signing key corresponding to the {@code publicKey}.
     * @param publicKey the {@link ECPublicKey} corresponding to the {@link Signing} key which was used to produce the {@code signature}.
     * @param signature the {@link Signature} produced by the {@link Signing} key corresponding to the {@code publicKey}.
     * @return an instance of the default {@link SignatureScheme} used, containing the {@code signature},
     * produced by the signing key corresponding to the {@code publicKey}.
     */
    Signatures concatenate(ECPublicKey publicKey, Signature signature);

    /**
     * Returns {@code true} if this collection of signatures contains no key-value mappings.
     *
     * @return {@code true} if this collection of signatures contains no key-value mappings
     */
    boolean isEmpty();

    /**
     * Nn empty collection of {@link Signature}s, with the default {@link SignatureScheme} used.
     */
    Signatures DEFAULT = new ECDSASignatures();

    /**
     * Returns an empty collection of {@link Signature}s, with the default {@link SignatureScheme} used.
     * @return an empty collection of {@link Signature}s, with the default {@link SignatureScheme} used.
     */
    static Signatures defaultEmptySignatures() {
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
    static Signatures defaultSingle(ECPublicKey publicKey, Signature signature) {
        return defaultEmptySignatures().concatenate(publicKey, signature);
    }
}
