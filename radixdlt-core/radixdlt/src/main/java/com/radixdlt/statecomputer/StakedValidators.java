/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wrapper class for registered validators
 */
public final class StakedValidators {
	private static final Comparator<ECPublicKey> keyOrdering = Comparator.comparing(ECPublicKey::euid);

	private final Map<ECPublicKey, UInt256> stake;
	private final Set<ValidatorParticle> validatorParticles;
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Map.Entry<ECPublicKey, UInt256>> validatorOrdering =
		Map.Entry.<ECPublicKey, UInt256>comparingByValue(stakeOrdering).thenComparing(Map.Entry.comparingByKey(keyOrdering));

	private final int minValidators;
	private final int maxValidators;

	private StakedValidators(
		int minValidators,
		int maxValidators,
		Set<ValidatorParticle> validatorParticles,
		Map<ECPublicKey, UInt256> stake
	) {
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
		this.validatorParticles = validatorParticles;
		this.stake = stake;
	}

	public static StakedValidators create(
		int minValidators,
		int maxValidators
	) {
		return new StakedValidators(minValidators, maxValidators, Set.of(), Map.of());
	}

	public StakedValidators add(ValidatorParticle particle) {
		var set = ImmutableSet.<ValidatorParticle>builder()
			.addAll(validatorParticles)
			.add(particle)
			.build();

		return new StakedValidators(minValidators, maxValidators, set, stake);
	}

	public StakedValidators remove(ValidatorParticle particle) {
		return new StakedValidators(
			minValidators,
			maxValidators,
			validatorParticles.stream()
				.filter(e -> !e.equals(particle))
				.collect(Collectors.toSet()),
			stake
		);
	}

	public UInt256 getStake(ECPublicKey validatorKey) {
		var s = stake.get(validatorKey);
		return (s == null) ? UInt256.ZERO : s;
	}

	public StakedValidators setStake(ECPublicKey delegatedKey, UInt256 amount) {
		final var nextStake = Stream.concat(
			Stream.of(Maps.immutableEntry(delegatedKey, amount)),
			this.stake.entrySet().stream().filter(e -> !delegatedKey.equals(e.getKey()))
		).collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

		return new StakedValidators(minValidators, maxValidators, validatorParticles, nextStake);
	}

	public BFTValidatorSet toValidatorSet() {
		var validatorMap = ImmutableMap.copyOf(
			Maps.filterKeys(stake, k -> validatorParticles.stream().map(ValidatorParticle::getKey).anyMatch(k::equals))
		);

		final var potentialValidators = validatorMap.entrySet().stream()
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

	public <T> List<T> map(BiFunction<ECPublicKey, ValidatorDetails, T> mapper) {
		return validatorParticles
			.stream()
			.map(p -> mapper.apply(p.getKey(), ValidatorDetails.fromParticle(p, getStake(p.getKey()))))
			.collect(Collectors.toList());
	}

	public <T> Optional<T> mapSingle(ECPublicKey validatorKey, Function<ValidatorDetails, T> mapper) {
		return validatorParticles.stream()
			.filter(p -> p.getKey().equals(validatorKey))
			.findFirst()
			.map(p -> ValidatorDetails.fromParticle(p, getStake(p.getKey())))
			.map(mapper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minValidators, maxValidators, validatorParticles, stake);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StakedValidators)) {
			return false;
		}

		var other = (StakedValidators) o;
		return this.minValidators == other.minValidators
			&& this.maxValidators == other.maxValidators
			&& Objects.equals(this.validatorParticles, other.validatorParticles)
			&& Objects.equals(this.stake, other.stake);
	}
}
