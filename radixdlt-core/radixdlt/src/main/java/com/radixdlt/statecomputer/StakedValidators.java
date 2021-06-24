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
import com.radixdlt.atommodel.validators.state.ValidatorData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.FunctionalUtils.removeKey;
import static com.radixdlt.utils.functional.FunctionalUtils.replaceEntry;

import static java.util.Optional.ofNullable;

/**
 * Wrapper class for registered validators
 */
public final class StakedValidators {
	private static final Comparator<ECPublicKey> keyOrdering = Comparator.comparing(ECPublicKey::euid);

	private final Map<ECPublicKey, UInt256> stake;
	private final Map<ECPublicKey, REAddr> owners;
	private final Map<ECPublicKey, Boolean> delegationFlags;
	private final Set<ValidatorData> validatorData;
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Map.Entry<ECPublicKey, UInt256>> validatorOrdering =
		Map.Entry.<ECPublicKey, UInt256>comparingByValue(stakeOrdering)
			.thenComparing(Map.Entry.comparingByKey(keyOrdering));

	private final int minValidators;
	private final int maxValidators;

	private StakedValidators(
		int minValidators,
		int maxValidators,
		Set<ValidatorData> validatorData,
		Map<ECPublicKey, UInt256> stake,
		Map<ECPublicKey, REAddr> owners,
		Map<ECPublicKey, Boolean> delegationFlags
	) {
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
		this.validatorData = validatorData;
		this.stake = stake;
		this.owners = owners;
		this.delegationFlags = delegationFlags;
	}

	public static StakedValidators create(
		int minValidators,
		int maxValidators
	) {
		return new StakedValidators(minValidators, maxValidators, Set.of(), Map.of(), Map.of(), Map.of());
	}

	public StakedValidators add(ValidatorData particle) {
		var set = ImmutableSet.<ValidatorData>builder()
			.addAll(validatorData)
			.add(particle)
			.build();

		return new StakedValidators(minValidators, maxValidators, set, stake, owners, delegationFlags);
	}

	public StakedValidators remove(ValidatorData particle) {
		return new StakedValidators(
			minValidators,
			maxValidators,
			validatorData.stream()
				.filter(e -> !e.equals(particle))
				.collect(Collectors.toSet()),
			stake,
			owners,
			delegationFlags
		);
	}

	public REAddr getOwner(ECPublicKey validatorKey) {
		return ofNullable(owners.get(validatorKey))
			.orElse(REAddr.ofPubKeyAccount(validatorKey));
	}

	public UInt256 getStake(ECPublicKey validatorKey) {
		return ofNullable(stake.get(validatorKey))
			.orElse(UInt256.ZERO);
	}

	public Boolean allowsDelegation(ECPublicKey validatorKey) {
		return ofNullable(delegationFlags.get(validatorKey))
			.orElse(Boolean.TRUE);
	}

	public UInt256 getOwnerStake(ECPublicKey key) {
		return getOwner(key)
			.publicKey()
			.map(this::getStake)
			.orElse(UInt256.ZERO);
	}

	public StakedValidators setAllowDelegationFlag(ECPublicKey validatorKey, boolean allowDelegation) {
		var nextFlags = replaceEntry(Maps.immutableEntry(validatorKey, allowDelegation), delegationFlags);
		return new StakedValidators(minValidators, maxValidators, validatorData, stake, owners, nextFlags);
	}

	public StakedValidators setOwner(ECPublicKey validatorKey, REAddr owner) {
		var nextOwners = replaceEntry(Maps.immutableEntry(validatorKey, owner), owners);
		return new StakedValidators(minValidators, maxValidators, validatorData, stake, nextOwners, delegationFlags);
	}

	public StakedValidators setStake(ECPublicKey delegatedKey, UInt256 amount) {
		var nextStake = replaceEntry(Maps.immutableEntry(delegatedKey, amount), stake);
		return new StakedValidators(minValidators, maxValidators, validatorData, nextStake, owners, delegationFlags);
	}

	// TODO: Remove add/remove from mainnet
	public StakedValidators add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		var nextAmount = stake.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

		return new StakedValidators(minValidators, maxValidators, validatorData, nextStakedAmounts, owners, delegationFlags);
	}

	public StakedValidators remove(ECPublicKey delegatedKey, UInt256 amount) {
		if (!this.stake.containsKey(delegatedKey)) {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}

		if (amount.isZero()) {
			return this;
		}

		final var oldAmount = this.stake.get(delegatedKey);
		final var comparison = amount.compareTo(oldAmount);

		if (comparison == 0) {
			// remove stake
			var nextStakedAmounts = removeKey(delegatedKey, stake);

			return new StakedValidators(minValidators, maxValidators, validatorData, nextStakedAmounts, owners, delegationFlags);
		} else if (comparison < 0) {
			// reduce stake
			var nextAmount = oldAmount.subtract(amount);
			var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

			return new StakedValidators(minValidators, maxValidators, validatorData, nextStakedAmounts, owners, delegationFlags);
		} else {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}
	}

	public BFTValidatorSet toValidatorSet() {
		var validatorMap = ImmutableMap.copyOf(
			Maps.filterKeys(stake, k -> validatorData.stream().map(ValidatorData::getKey).anyMatch(k::equals))
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

	public <T> List<T> map(Function<ValidatorDetails, T> mapper) {
		return validatorData
			.stream()
			.map(this::fillDetails)
			.map(mapper::apply)
			.collect(Collectors.toList());
	}

	public long count() {
		return validatorData.size();
	}

	public <T> Optional<T> mapSingle(ECPublicKey validatorKey, Function<ValidatorDetails, T> mapper) {
		return validatorData.stream()
			.filter(particle -> particle.getKey().equals(validatorKey))
			.findFirst()
			.map(this::fillDetails)
			.map(mapper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minValidators, maxValidators, validatorData, stake, owners, delegationFlags);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StakedValidators)) {
			return false;
		}

		var other = (StakedValidators) o;
		return minValidators == other.minValidators
			&& maxValidators == other.maxValidators
			&& Objects.equals(validatorData, other.validatorData)
			&& Objects.equals(stake, other.stake)
			&& Objects.equals(owners, other.owners)
			&& Objects.equals(delegationFlags, other.delegationFlags);
	}

	private ValidatorDetails fillDetails(ValidatorData particle) {
		return ValidatorDetails.fromParticle(
			particle,
			getOwner(particle.getKey()),
			getStake(particle.getKey()),
			getOwnerStake(particle.getKey()),
			allowsDelegation(particle.getKey())
		);
	}
}
