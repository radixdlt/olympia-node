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
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.engine.RadixEngineAtom;
import com.radixdlt.engine.RadixEngine.RadixEngineBranch;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;

/**
 * Utility class to build a validator set from output of
 * stake and validator computers.
 */
public final class ValidatorSetBuilder {
	@VisibleForTesting
	static final Comparator<ECPublicKey> keyOrdering = Comparator.comparing(ECPublicKey::euid);
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Pair<ECPublicKey, UInt256>> validatorOrdering =
		Comparator.comparing((Pair<ECPublicKey, UInt256> p) -> p.getSecond(), stakeOrdering)
			.thenComparing(Pair::getFirst, keyOrdering);

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
		RadixEngineBranch<? extends RadixEngineAtom> branch
	) {
		return buildValidatorSet(
			branch.getComputedState(RadixEngineValidatorsComputer.class),
			branch.getComputedState(RadixEngineStakeComputer.class)
		);
	}

	public BFTValidatorSet buildValidatorSet(
		RadixEngine<? extends RadixEngineAtom> engine
	) {
		return buildValidatorSet(
			engine.getComputedState(RadixEngineValidatorsComputer.class),
			engine.getComputedState(RadixEngineStakeComputer.class)
		);
	}

	private BFTValidatorSet buildValidatorSet(
		RadixEngineValidatorsComputer validatorsComputer,
		RadixEngineStakeComputer stakeComputer
	) {
		final var validators = validatorsComputer.activeValidators();
		final var stakedAmounts = stakeComputer.stakedAmounts(validators);

		final var potentialValidators = validators.stream()
			.filter(stakedAmounts::containsKey)
			.map(k -> Pair.of(k, stakedAmounts.get(k)))
			.filter(p -> !p.getSecond().isZero())
			.collect(Collectors.toList());

		if (potentialValidators.size() < this.minValidators) {
			return null;
		}

		potentialValidators.sort(validatorOrdering);
		final var lastIndex = Math.min(this.maxValidators, potentialValidators.size());
		return BFTValidatorSet.from(
			potentialValidators.subList(0, lastIndex).stream()
				.map(p -> BFTValidator.from(BFTNode.create(p.getFirst()), p.getSecond()))
		);
	}
}