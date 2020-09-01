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

package com.radixdlt.statecomputer;

import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Helper class to build validator sets from radix engine state.
 */
@NotThreadSafe
public final class RadixEngineValidatorSetBuilder {
	private final HashSet<ECPublicKey> validators;
	private final Predicate<Set<ECPublicKey>> validatorSetCheck;
	private HashSet<ECPublicKey> lastGoodSet;

	public RadixEngineValidatorSetBuilder(Set<ECPublicKey> initialSet, Predicate<Set<ECPublicKey>> validatorSetCheck) {
		if (!validatorSetCheck.test(initialSet)) {
			throw new IllegalArgumentException("Initial validator set should pass check");
		}
		this.validators = new HashSet<>(initialSet);
		this.lastGoodSet = new HashSet<>(initialSet);
		this.validatorSetCheck = Objects.requireNonNull(validatorSetCheck);
	}

	public RadixEngineValidatorSetBuilder removeValidator(RadixAddress validatorAddress) {
		this.validators.remove(validatorAddress.getPublicKey());
		if (this.validatorSetCheck.test(this.validators)) {
			this.lastGoodSet = new HashSet<>(this.validators);
		}
		return this;
	}

	public RadixEngineValidatorSetBuilder addValidator(RadixAddress validatorAddress) {
		this.validators.add(validatorAddress.getPublicKey());
		if (this.validatorSetCheck.test(this.validators)) {
			this.lastGoodSet = new HashSet<>(this.validators);
		}
		return this;
	}

	BFTValidatorSet build() {
		return BFTValidatorSet.from(
			lastGoodSet.stream()
				.map(BFTNode::create)
				.map(node -> BFTValidator.from(node, UInt256.ONE))
		);
	}
}
