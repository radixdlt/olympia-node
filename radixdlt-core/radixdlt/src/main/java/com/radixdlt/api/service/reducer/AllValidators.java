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

package com.radixdlt.api.service.reducer;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.radixdlt.atommodel.validators.state.ValidatorMetaData;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.ValidatorDetails;
import com.radixdlt.utils.UInt256;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.radixdlt.utils.functional.FunctionalUtils.removeKey;
import static com.radixdlt.utils.functional.FunctionalUtils.replaceEntry;

import static java.util.Optional.ofNullable;

/**
 * Wrapper class for registered validators
 */
public final class AllValidators {
	private final Map<ECPublicKey, ValidatorMetaData> metadata;
	private final Map<ECPublicKey, UInt256> stake;
	private final Map<ECPublicKey, REAddr> owners;
	private final Map<ECPublicKey, Boolean> delegationFlags;
	private final Map<ECPublicKey, Integer> rakes;
	private final Set<ECPublicKey> registered;

	private AllValidators(
		Set<ECPublicKey> registered,
		Map<ECPublicKey, UInt256> stake,
		Map<ECPublicKey, REAddr> owners,
		Map<ECPublicKey, Boolean> delegationFlags,
		Map<ECPublicKey, ValidatorMetaData> metadata,
		Map<ECPublicKey, Integer> rakes
	) {
		this.registered = registered;
		this.stake = stake;
		this.owners = owners;
		this.delegationFlags = delegationFlags;
		this.metadata = metadata;
		this.rakes = rakes;
	}

	public static AllValidators create() {
		return new AllValidators(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	public AllValidators set(ValidatorMetaData metaData) {
		var nextMetadata = replaceEntry(Maps.immutableEntry(metaData.getValidatorKey(), metaData), metadata);
		return new AllValidators(registered, stake, owners, delegationFlags, nextMetadata, rakes);
	}

	public AllValidators setRegistered(ECPublicKey validatorKey, boolean isRegistered) {
		if (registered.contains(validatorKey) == isRegistered) {
			return this;
		}
		if (!isRegistered) {
			return new AllValidators(
				registered.stream()
					.filter(e -> !e.equals(validatorKey))
					.collect(Collectors.toSet()),
				stake,
				owners,
				delegationFlags,
				metadata,
				rakes
			);
		} else {
			var set = ImmutableSet.<ECPublicKey>builder()
				.addAll(registered)
				.add(validatorKey)
				.build();

			return new AllValidators(set, stake, owners, delegationFlags, metadata, rakes);
		}
	}

	public AllValidators add(ECPublicKey validatorKey) {
		var set = ImmutableSet.<ECPublicKey>builder()
			.addAll(registered)
			.add(validatorKey)
			.build();

		return new AllValidators(set, stake, owners, delegationFlags, metadata, rakes);
	}

	public AllValidators remove(ECPublicKey validatorKey) {
		return new AllValidators(
			registered.stream()
				.filter(e -> !e.equals(validatorKey))
				.collect(Collectors.toSet()),
			stake,
			owners,
			delegationFlags,
			metadata,
			rakes
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

	private int getRake(ECPublicKey validatorKey) {
		return ofNullable(rakes.get(validatorKey)).orElse(0);
	}

	public AllValidators setAllowDelegationFlag(ECPublicKey validatorKey, boolean allowDelegation) {
		var nextFlags = replaceEntry(Maps.immutableEntry(validatorKey, allowDelegation), delegationFlags);
		return new AllValidators(registered, stake, owners, nextFlags, metadata, rakes);
	}

	public AllValidators setOwner(ECPublicKey validatorKey, REAddr owner) {
		var nextOwners = replaceEntry(Maps.immutableEntry(validatorKey, owner), owners);
		return new AllValidators(registered, stake, nextOwners, delegationFlags, metadata, rakes);
	}

	public AllValidators setStake(ECPublicKey delegatedKey, UInt256 amount) {
		var nextStake = replaceEntry(Maps.immutableEntry(delegatedKey, amount), stake);
		return new AllValidators(registered, nextStake, owners, delegationFlags, metadata, rakes);
	}

	public AllValidators setRake(ECPublicKey validatorKey, int curRakePercentage) {
		var nextRakes = replaceEntry(Maps.immutableEntry(validatorKey, curRakePercentage), rakes);
		return new AllValidators(registered, stake, owners, delegationFlags, metadata, nextRakes);
	}

	// TODO: Remove add/remove from mainnet
	public AllValidators add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		var nextAmount = stake.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

		return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, rakes);
	}

	public AllValidators remove(ECPublicKey delegatedKey, UInt256 amount) {
		if (!stake.containsKey(delegatedKey)) {
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

			return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, rakes);
		} else if (comparison < 0) {
			// reduce stake
			var nextAmount = oldAmount.subtract(amount);
			var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

			return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, rakes);
		} else {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}
	}

	public <T> List<T> map(Function<ValidatorDetails, T> mapper) {
		var validators = ImmutableSet.<ECPublicKey>builder()
			.addAll(registered)
			.addAll(metadata.keySet())
			.addAll(stake.keySet())
			.addAll(owners.keySet())
			.addAll(delegationFlags.keySet())
			.addAll(rakes.keySet())
			.build();

		return validators
			.stream()
			.map(this::fillDetails)
			.map(mapper)
			.collect(Collectors.toList());
	}

	public long count() {
		return registered.size();
	}

	@Override
	public int hashCode() {
		return Objects.hash(registered, stake, owners, rakes, delegationFlags, metadata);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AllValidators)) {
			return false;
		}

		var other = (AllValidators) o;
		return Objects.equals(metadata, other.metadata)
			&& Objects.equals(registered, other.registered)
			&& Objects.equals(stake, other.stake)
			&& Objects.equals(owners, other.owners)
			&& Objects.equals(rakes, other.rakes)
			&& Objects.equals(delegationFlags, other.delegationFlags);
	}

	private ValidatorDetails fillDetails(ECPublicKey validatorKey) {
		return ValidatorDetails.fromParticle(
			getMetadata(validatorKey),
			getOwner(validatorKey),
			getStake(validatorKey),
			getOwnerStake(validatorKey),
			allowsDelegation(validatorKey),
			registered.contains(validatorKey),
			getRake(validatorKey)
		);
	}
}
