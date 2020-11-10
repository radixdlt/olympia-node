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

import com.google.common.collect.ImmutableSet;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import java.util.function.Predicate;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Helper class to build validator sets from radix engine state.
 */
@NotThreadSafe
public final class RadixEngineValidatorSetBuilder {
	private final ImmutableSet<ECPublicKey> validators;
	private final ImmutableSet<ECPublicKey> lastGoodSet;
	private final Predicate<ImmutableSet<ECPublicKey>> validatorSetCheck;

	public RadixEngineValidatorSetBuilder(ImmutableSet<ECPublicKey> initialSet, Predicate<ImmutableSet<ECPublicKey>> validatorSetCheck) {
		if (!validatorSetCheck.test(initialSet)) {
			throw new IllegalArgumentException(
				String.format("Initial validator set %s should pass check: %s", initialSet, validatorSetCheck)
			);
		}
		this.validators = ImmutableSet.copyOf(initialSet);
		this.lastGoodSet = ImmutableSet.copyOf(initialSet);
		this.validatorSetCheck = validatorSetCheck;
	}

	private RadixEngineValidatorSetBuilder(
		ImmutableSet<ECPublicKey> validators,
		ImmutableSet<ECPublicKey> lastGoodSet,
		Predicate<ImmutableSet<ECPublicKey>> validatorSetCheck
	) {
		this.validators = validators;
		this.lastGoodSet = lastGoodSet;
		this.validatorSetCheck = validatorSetCheck;
	}

	public RadixEngineValidatorSetBuilder removeValidator(RadixAddress validatorAddress) {
		ImmutableSet<ECPublicKey> nextValidators = validators.stream()
			.filter(e -> !e.equals(validatorAddress.getPublicKey()))
			.collect(ImmutableSet.toImmutableSet());

		if (this.validatorSetCheck.test(nextValidators)) {
			return new RadixEngineValidatorSetBuilder(nextValidators, nextValidators, validatorSetCheck);
		}

		return new RadixEngineValidatorSetBuilder(nextValidators, lastGoodSet, validatorSetCheck);
	}

	public RadixEngineValidatorSetBuilder addValidator(RadixAddress validatorAddress) {
		ImmutableSet<ECPublicKey> nextValidators = ImmutableSet.<ECPublicKey>builder()
			.addAll(validators)
			.add(validatorAddress.getPublicKey())
			.build();

		if (this.validatorSetCheck.test(nextValidators)) {
			return new RadixEngineValidatorSetBuilder(nextValidators, nextValidators, validatorSetCheck);
		}

		return new RadixEngineValidatorSetBuilder(nextValidators, lastGoodSet, validatorSetCheck);
	}

	BFTValidatorSet build() {
		return BFTValidatorSet.from(
			lastGoodSet.stream()
				.map(BFTNode::create)
				.map(node -> BFTValidator.from(node, UInt256.ONE))
		);
	}
}
