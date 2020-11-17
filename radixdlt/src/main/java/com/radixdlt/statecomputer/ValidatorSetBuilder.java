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

package com.radixdlt.statecomputer;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

/**
 * Utility class to build a validator set from output of
 * stake and validator computers.
 */
public final class ValidatorSetBuilder {
	@VisibleForTesting
	static final Comparator<ECPublicKey> keyOrdering = Comparator.comparing(ECPublicKey::euid);
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Map.Entry<ECPublicKey, UInt256>> validatorOrdering =
		Map.Entry.<ECPublicKey, UInt256>comparingByValue(stakeOrdering).thenComparing(Map.Entry.comparingByKey(keyOrdering));

	private final int minValidators;
	private final int maxValidators;

	private ValidatorSetBuilder(int minValidators, int maxValidators) {
		if (minValidators <= 0) {
			throw new IllegalArgumentException("minValidators must be > 0: " + minValidators);
		}
		if (minValidators > maxValidators) {
			throw new IllegalArgumentException(
				String.format("minValidators must be <= maxValidators, but %s > %s", minValidators, maxValidators)
			);
		}
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
	}

	public static ValidatorSetBuilder create(int minValidators, int maxValidators) {
		return new ValidatorSetBuilder(minValidators, maxValidators);
	}

	public BFTValidatorSet buildValidatorSet(
		RadixEngineValidatorsComputer validatorsComputer,
		RadixEngineStakeComputer stakeComputer
	) {
		final var validators = validatorsComputer.activeValidators();
		final var stakedAmounts = stakeComputer.stakedAmounts(validators);
		return buildValidatorSet(stakedAmounts);
	}

	public BFTValidatorSet buildValidatorSet(
		ImmutableMap<ECPublicKey, UInt256> stakedAmounts
	) {
		final var potentialValidators = stakedAmounts.entrySet().stream()
			.filter(e -> !e.getValue().isZero())
			.collect(Collectors.toList());

		if (potentialValidators.size() < this.minValidators) {
			return null;
		}

		potentialValidators.sort(validatorOrdering);
		final var lastIndex = Math.min(this.maxValidators, potentialValidators.size());
		return BFTValidatorSet.from(
			potentialValidators.subList(0, lastIndex).stream()
				.map(p -> BFTValidator.from(BFTNode.create(p.getKey()), p.getValue()))
		);
	}
}