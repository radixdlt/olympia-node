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

package com.radixdlt.consensus.validators;

import com.radixdlt.utils.UInt256;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.radixdlt.crypto.ECDSASignature;
import com.radixdlt.crypto.ECDSASignatures;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

/**
 * Keeps track of current validation state for a thing that
 * needs multiple correct signatures for a quorum.
 */
public final class ValidationState {

	private final Hash hash;
	private final ValidatorSet validatorSet;
	private final ConcurrentMap<ECPublicKey, ECDSASignature> signedKeys;

	/**
	 * Construct empty validation state for given hash and set of validator keys.
	 *
	 * @param hash The hash to verify signatures against
	 * @param validatorSet The validator set
	 */
	public static ValidationState forValidatorSet(Hash hash, ValidatorSet validatorSet) {
		return new ValidationState(hash, validatorSet);
	}

	private ValidationState(Hash hash, ValidatorSet validatorSet) {
		this.hash = Objects.requireNonNull(hash);
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.signedKeys = Maps.newConcurrentMap();
	}

	/**
	 * Verifies the specified signature and key against our hash.
	 * If the key and signature passes verification, they are added to the list
	 * of signing keys and signatures.
	 *
	 * @param key The public key to use for signature verification
	 * @param signature The signature to verify
	 * @return whether a quorum has been formed or not
	 */
	public boolean addSignature(ECPublicKey key, ECDSASignature signature) {
		if (validatorSet.containsKey(key)
			&& (this.signedKeys.containsKey(key) || key.verify(this.hash, signature))) {
			this.signedKeys.put(key, signature);
		}
		return complete();
	}

	/**
	 * Returns {@code true} if we have enough valid signatures to form a quorum.
	 *
	 * @return {@code true} if we have enough valid signatures to form a quorum,
	 */
	public boolean complete() {
		UInt256 currentPower = this.validatorSet.getPower(this.signedKeys.keySet());
		return currentPower.compareTo(threshold(this.validatorSet.getTotalPower())) >= 0;
	}

	/**
	 * Returns an {@link ECDSASignatures} object for our current set of valid signatures.
	 *
	 * @return an {@link ECDSASignatures} object for our current set of valid signatures
	 */
	public ECDSASignatures signatures() {
		return new ECDSASignatures(ImmutableMap.copyOf(this.signedKeys));
	}

	@VisibleForTesting
	static UInt256 threshold(UInt256 n) {
		return n.subtract(acceptableFaults(n));
	}

	@VisibleForTesting
	static UInt256 acceptableFaults(UInt256 n) {
		// Compute acceptable faults based on Byzantine limit n = 3f + 1
		// i.e. f = (n - 1) / 3
		return n.isZero() ? n : n.decrement().divide(UInt256.THREE);
	}

	@Override
	public int hashCode() {
		return Objects.hash(hash, validatorSet, signedKeys);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ValidationState) {
			ValidationState that = (ValidationState) obj;
			return Objects.equals(this.hash, that.hash)
				&& Objects.equals(this.validatorSet, that.validatorSet)
				&& Objects.equals(this.signedKeys, that.signedKeys);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[hash=%s, validatorSet=%s, signedKeys=%s]",
			getClass().getSimpleName(), hash, validatorSet, signedKeys);
	}
}
