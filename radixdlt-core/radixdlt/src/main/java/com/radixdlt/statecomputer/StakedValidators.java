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
import com.radixdlt.atommodel.validators.state.ValidatorMetaData;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidator;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.KeyComparator;
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
	private final Map<ECPublicKey, ValidatorMetaData> metadata;
	private final Map<ECPublicKey, UInt256> stake;
	private final Map<ECPublicKey, REAddr> owners;
	private final Map<ECPublicKey, Boolean> delegationFlags;
	private final Set<ECPublicKey> registered;
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Map.Entry<ECPublicKey, UInt256>> validatorOrdering =
		Map.Entry.<ECPublicKey, UInt256>comparingByValue(stakeOrdering)
			.thenComparing(Map.Entry.comparingByKey(KeyComparator.instance()));

	private final int minValidators;
	private final int maxValidators;

	private StakedValidators(
		int minValidators,
		int maxValidators,
		Set<ECPublicKey> registered,
		Map<ECPublicKey, UInt256> stake,
		Map<ECPublicKey, REAddr> owners,
		Map<ECPublicKey, Boolean> delegationFlags,
		Map<ECPublicKey, ValidatorMetaData> metadata
	) {
		this.minValidators = minValidators;
		this.maxValidators = maxValidators;
		this.registered = registered;
		this.stake = stake;
		this.owners = owners;
		this.delegationFlags = delegationFlags;
		this.metadata = metadata;
	}

	public static StakedValidators create(
		int minValidators,
		int maxValidators
	) {
		return new StakedValidators(minValidators, maxValidators, Set.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	public StakedValidators set(ValidatorMetaData metaData) {
		var nextMetadata = replaceEntry(Maps.immutableEntry(metaData.getKey(), metaData), metadata);
		return new StakedValidators(minValidators, maxValidators, registered, stake, owners, delegationFlags, nextMetadata);
	}

	public StakedValidators setRegistered(ECPublicKey validatorKey, boolean isRegistered) {
		if (registered.contains(validatorKey) == isRegistered) {
			return this;
		}
		if (!isRegistered) {
			return new StakedValidators(
				minValidators,
				maxValidators,
				registered.stream()
					.filter(e -> !e.equals(validatorKey))
					.collect(Collectors.toSet()),
				stake,
				owners,
				delegationFlags,
				metadata
			);
		} else {
			var set = ImmutableSet.<ECPublicKey>builder()
				.addAll(registered)
				.add(validatorKey)
				.build();

			return new StakedValidators(minValidators, maxValidators, set, stake, owners, delegationFlags, metadata);
		}
	}

	public StakedValidators add(ECPublicKey validatorKey) {
		var set = ImmutableSet.<ECPublicKey>builder()
			.addAll(registered)
			.add(validatorKey)
			.build();

		return new StakedValidators(minValidators, maxValidators, set, stake, owners, delegationFlags, metadata);
	}

	public StakedValidators remove(ECPublicKey validatorKey) {
		return new StakedValidators(
			minValidators,
			maxValidators,
			registered.stream()
				.filter(e -> !e.equals(validatorKey))
				.collect(Collectors.toSet()),
			stake,
			owners,
			delegationFlags,
			metadata
		);
	}

	public ValidatorMetaData getMetadata(ECPublicKey validatorKey) {
		return ofNullable(metadata.get(validatorKey)).orElse(new ValidatorMetaData(validatorKey));
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
		return new StakedValidators(minValidators, maxValidators, registered, stake, owners, nextFlags, metadata);
	}

	public StakedValidators setOwner(ECPublicKey validatorKey, REAddr owner) {
		var nextOwners = replaceEntry(Maps.immutableEntry(validatorKey, owner), owners);
		return new StakedValidators(minValidators, maxValidators, registered, stake, nextOwners, delegationFlags, metadata);
	}

	public StakedValidators setStake(ECPublicKey delegatedKey, UInt256 amount) {
		var nextStake = replaceEntry(Maps.immutableEntry(delegatedKey, amount), stake);
		return new StakedValidators(minValidators, maxValidators, registered, nextStake, owners, delegationFlags, metadata);
	}

	// TODO: Remove add/remove from mainnet
	public StakedValidators add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		var nextAmount = stake.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

		return new StakedValidators(minValidators, maxValidators, registered, nextStakedAmounts, owners, delegationFlags, metadata);
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

			return new StakedValidators(minValidators, maxValidators, registered, nextStakedAmounts, owners, delegationFlags, metadata);
		} else if (comparison < 0) {
			// reduce stake
			var nextAmount = oldAmount.subtract(amount);
			var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

			return new StakedValidators(minValidators, maxValidators, registered, nextStakedAmounts, owners, delegationFlags, metadata);
		} else {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}
	}

	public BFTValidatorSet toValidatorSet() {
		var validatorMap = ImmutableMap.copyOf(
			Maps.filterKeys(stake, k -> registered.stream().anyMatch(k::equals))
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
		return registered
			.stream()
			.map(this::fillDetails)
			.map(mapper)
			.collect(Collectors.toList());
	}

	public long count() {
		return registered.size();
	}

	public <T> Optional<T> mapSingle(ECPublicKey validatorKey, Function<ValidatorDetails, T> mapper) {
		return registered.stream()
			.filter(key -> key.equals(validatorKey))
			.findFirst()
			.map(this::fillDetails)
			.map(mapper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minValidators, maxValidators, registered, stake, owners, delegationFlags, metadata);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof StakedValidators)) {
			return false;
		}

		var other = (StakedValidators) o;
		return minValidators == other.minValidators
			&& maxValidators == other.maxValidators
			&& Objects.equals(metadata, other.metadata)
			&& Objects.equals(registered, other.registered)
			&& Objects.equals(stake, other.stake)
			&& Objects.equals(owners, other.owners)
			&& Objects.equals(delegationFlags, other.delegationFlags);
	}

	private ValidatorDetails fillDetails(ECPublicKey validatorKey) {
		return ValidatorDetails.fromParticle(
			getMetadata(validatorKey),
			getOwner(validatorKey),
			getStake(validatorKey),
			getOwnerStake(validatorKey),
			allowsDelegation(validatorKey)
		);
	}
}
