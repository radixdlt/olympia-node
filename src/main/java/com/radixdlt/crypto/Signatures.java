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
     * Checks whether or not this collection of {@link Signature}s and their corresponding {@link ECPublicKey}s indeed has signed the message,
     * requiring that at least {@code requiredMinimumNumberOfValidSignatures} of the signatures are valid.
     * @param message The hashed data to check against (the data that has been signed).
     * @param requiredMinimumNumberOfValidSignatures The required minimum number of valid signatures.
     * @return If the number of valid signatures having signed {@code message} is greater or equal to {@code requiredMinimumNumberOfValidSignatures}
     */
    boolean hasSignedMessage(Hash message, int requiredMinimumNumberOfValidSignatures);

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
}
