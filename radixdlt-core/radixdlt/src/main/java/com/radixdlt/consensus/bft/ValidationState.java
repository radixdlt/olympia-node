/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.radixdlt.utils.UInt256;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.TimestampedECDSASignature;
import com.radixdlt.consensus.TimestampedECDSASignatures;
import com.radixdlt.crypto.ECDSASignature;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Keeps track of current validation state for a thing that
 * needs multiple correct signatures for a quorum.
 */
@NotThreadSafe
public final class ValidationState {

	private final BFTValidatorSet validatorSet;
	private final Map<BFTNode, TimestampedECDSASignature> signedNodes;
	private transient UInt256 signedPower;
	private final transient UInt256 threshold;

	/**
	 * Construct empty validation state for given hash and set of validator keys.
	 *
	 * @param validatorSet The validator set
	 */
	public static ValidationState forValidatorSet(BFTValidatorSet validatorSet) {
		return new ValidationState(validatorSet);
	}

	private ValidationState(BFTValidatorSet validatorSet) {
		this.validatorSet = Objects.requireNonNull(validatorSet);
		this.signedNodes = new HashMap<>();
		this.signedPower = UInt256.ZERO;
		this.threshold = threshold(validatorSet.getTotalPower());
	}

	/**
	 * Removes the signature for the specified key, if present.
	 *
	 * @param node the node who's signature is to be removed
	 */
	public void removeSignature(BFTNode node) {
		if (this.validatorSet.containsNode(node)) {
			this.signedNodes.computeIfPresent(node, (k, v) -> {
				this.signedPower = this.signedPower.subtract(this.validatorSet.getPower(node));
				return null;
			});
		}
	}

	/**
	 * Adds key and signature to our list of signing keys and signatures.
	 * Note that it is assumed that signature validation is performed
	 * elsewhere.
	 *
	 * @param node The node
	 * @param timestamp The timestamp of the signature
	 * @param signature The signature to verify
	 * @return whether the key was added or not
	 */
	public boolean addSignature(BFTNode node, long timestamp, ECDSASignature signature) {
		if (validatorSet.containsNode(node)
			&& !this.signedNodes.containsKey(node)) {
			this.signedNodes.computeIfAbsent(node, k -> {
				UInt256 weight = this.validatorSet.getPower(node);
				this.signedPower = this.signedPower.add(weight);
				return TimestampedECDSASignature.from(timestamp, signature);
			});
			return true;
		}
		return false;
	}

	/**
	 * Return {@code true} if we have not yet accumulated any valid signatures.
	 * @return {@code true} if we have not accumulated any signatures, {@code false} otherwise.
	 */
	public boolean isEmpty() {
		return this.signedNodes.isEmpty();
	}

	/**
	 * Returns {@code true} if we have enough valid signatures to form a quorum.
	 *
	 * @return {@code true} if we have enough valid signatures to form a quorum,
	 */
	public boolean complete() {
		return signedPower.compareTo(threshold) >= 0;
	}

	/**
	 * Returns an {@link ECDSASignatures} object for our current set of valid signatures.
	 *
	 * @return an {@link ECDSASignatures} object for our current set of valid signatures
	 */
	public TimestampedECDSASignatures signatures() {
		return new TimestampedECDSASignatures(ImmutableMap.copyOf(this.signedNodes));
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
		return Objects.hash(validatorSet, signedNodes);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ValidationState) {
			ValidationState that = (ValidationState) obj;
			return Objects.equals(this.validatorSet, that.validatorSet)
				&& Objects.equals(this.signedNodes, that.signedNodes);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[validatorSet=%s, signedNodes=%s]",
			getClass().getSimpleName(), validatorSet, signedNodes);
	}
}
