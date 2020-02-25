/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.crypto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;

import java.util.List;
import java.util.Map;

/**
 * A set of cryptographically secure signatures, specifying which signature was used
 */
class SignaturesImpl<T extends Signature> implements Signatures {
    // Placeholder for the serializer ID
    @JsonProperty(SerializerConstants.SERIALIZER_NAME)
    @DsonOutput(DsonOutput.Output.ALL)
    private SerializerDummy serializer = SerializerDummy.DUMMY;

    @JsonProperty("version")
    @DsonOutput(DsonOutput.Output.ALL)
    private short version = 100;

    @JsonProperty("signatures")
    @DsonOutput(DsonOutput.Output.ALL)
    private ImmutableMap<ECPublicKey, T> keyToSignature;

    private final Maps.EntryTransformer<ECPublicKey, T, Signature> transformer = (key, value) -> value;

    @Override
    public SignatureScheme signatureScheme() {
        throw new IllegalStateException("Implement me in subclass");
    }

    @Override
    public Map<ECPublicKey, Signature> keyToSignatures() {
       return Maps.transformEntries(this.keyToSignature, this.transformer);
    }

    private final Class<T> signatureType;

    /**
     * Returns a new instance of {@link Signatures} of type {@code signatureType}, containing {@code publicKey} which corresponding
     * {@link Signing} key was used to produce the {@code signature}.
     * @param signatureType The {@link Signature} type of {@code signature}
     * @param publicKey the {@link ECPublicKey} corresponding to the {@link Signing} key which was used to produce the {@code signature}.
     * @param signature the {@link Signature} produced by the {@link Signing} key corresponding to the {@code publicKey}.
     */
    public SignaturesImpl(Class<T> signatureType, ECPublicKey publicKey, T signature) {
        this.keyToSignature = ImmutableMap.of(publicKey, signature);
        this.signatureType = signatureType;
    }

    /**
     * Returns a new instance of {@link Signatures} of type {@code signatureType}, containing {@code keyToSignature}.
     * @param signatureType The {@link Signature} type of {@code signature}
     * @param keyToSignature The map of {@link Signature}s and their corresponding {@link ECPublicKey}
     */
    public SignaturesImpl(Class<T> signatureType, Map<ECPublicKey, ? extends T> keyToSignature) {
        this.keyToSignature = ImmutableMap.copyOf(keyToSignature);
        this.signatureType = signatureType;
    }

    /**
     * Returns a new instance of {@link Signatures} of type {@code signatureType}, containing {@code keyToSignature}.
     * @param signatureType The {@link Signature} type of {@code signature}
     * @param keyToSignature The map of {@link Signature}s and their corresponding {@link ECPublicKey}
     */
    public SignaturesImpl(Class<T> signatureType, ImmutableMap<ECPublicKey, T> keyToSignature) {
        this.keyToSignature = keyToSignature;
        this.signatureType = signatureType;
    }

    /**
     * Returns an empty collection of {@link Signature}s, which might be {@link #concatenate(ECPublicKey, Signature) concatenated}
     * with an instance of {@code signatureType} at at a later point in time.
     * @param signatureType The type of {@link Signature}
     */
    public SignaturesImpl(Class<T> signatureType) {
        this.keyToSignature = ImmutableMap.of();
        this.signatureType = signatureType;
    }

    @Override
    public Signatures concatenate(ECPublicKey publicKey, Signature signature) {
        if (!signatureType.isInstance(signature)) {
            throw new IllegalArgumentException(
                    String.format("Expected 'signature' to be of type '%s' but got '%s'",
                            this.signatureType.getName(), signature.getClass().getName()
                    )
            );
        }
        ImmutableMap.Builder<ECPublicKey, T> builder = ImmutableMap.builder();
        builder.putAll(this.keyToSignature);
        builder.put(publicKey, this.signatureType.cast(signature));
       return new SignaturesImpl<T>(this.signatureType, builder.build());
    }

    @Override
    public Class<? extends Signature> signatureType() {
        return this.signatureType;
    }

    @Override
    public boolean isEmpty() {
        return this.keyToSignature.isEmpty();
    }

    @Override
    public boolean hasSignedMessage(Hash message, int requiredMinimumNumberOfValidSignatures) {
        List<Boolean> validArray = this.keyToSignatures().entrySet().stream()
                .map(e -> e.getKey().verify(message, (ECDSASignature) e.getValue()))
                .collect(ImmutableList.toImmutableList());

        return validArray.size() >= requiredMinimumNumberOfValidSignatures;
    }

    @Override
    public String toString() {
        return String.format(
                "%s{keyToSignature=%s, signatureType=%s}",
                getClass().getSimpleName(),
                keyToSignature,
                signatureType
        );
    }
}