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

/**
 * An entity capable of producing a {@link Signature} of type {@code <T>} using some {@link SignatureScheme}.
 * @param <T>
 */
public interface Signing<T extends Signature> {

    /**
     * The {@link SignatureScheme} in {@link #sign(byte[]) sign} to produce a {@link Signature}.
     * @return The {@link SignatureScheme} in {@link #sign(byte[]) sign} to produce a {@link Signature}.
     */
    SignatureScheme signatureScheme();

    /**
     * Produces a {@link Signature} of type {@code <T>} of {@code hash}.
     * @param hash A hash of some message to sign as a byte array.
     * @return A {@link Signature} of type {@code <T>} of {@code hash}.
     * @throws CryptoException
     */
    T sign(byte[] hash) throws CryptoException;

    /**
     * Checks if signing entity is capable of producing a {@link Signature} matching the type {@code} signatureType
     * @param signatureType the type of {@link Signature} we want to check if this signing entity can produce.
     * @param <U> the type of {@link Signature} we want to check if this signing entity can produce.
     * @return Checks if signing entity is capable of producing a {@link Signature} matching the type {@code} signatureType
     */
    <U extends Signature> boolean canProduceSignatureOfType(Class<U> signatureType);

    /**
     * Produces a {@link Signature} of type {@code <T>} of {@code hash}.
     * @param hash A hash of some message to sign.
     * @return A {@link Signature} of type {@code <T>} of {@code hash}.
     * @throws CryptoException
     */
    default T sign(Hash hash) throws CryptoException {
        return sign(hash.toByteArray());
    }
}
