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
import com.radixdlt.atommodel.validators.state.ValidatorParticle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.ValidatorDetails;
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
 * Wrapper class for all validators
 */
public final class AllValidators {
	private static final Comparator<ECPublicKey> keyOrdering = Comparator.comparing(ECPublicKey::euid);

	private final Map<ECPublicKey, UInt256> stake;
	private final Map<ECPublicKey, REAddr> owners;
	private final Map<ECPublicKey, Boolean> delegationFlags;
	private final Map<ECPublicKey, Integer> rakes;
	private final Set<ValidatorParticle> validatorParticles;
	private static final Comparator<UInt256> stakeOrdering = Comparator.reverseOrder();
	private static final Comparator<Map.Entry<ECPublicKey, UInt256>> validatorOrdering =
		Map.Entry.<ECPublicKey, UInt256>comparingByValue(stakeOrdering)
			.thenComparing(Map.Entry.comparingByKey(keyOrdering));

	private AllValidators(
		Set<ValidatorParticle> validatorParticles,
		Map<ECPublicKey, UInt256> stake,
		Map<ECPublicKey, REAddr> owners,
		Map<ECPublicKey, Boolean> delegationFlags,
		Map<ECPublicKey, Integer> rakes
	) {
		this.validatorParticles = validatorParticles;
		this.stake = stake;
		this.owners = owners;
		this.delegationFlags = delegationFlags;
		this.rakes = rakes;
	}

	public static AllValidators create(
	) {
		return new AllValidators(Set.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	public AllValidators add(ValidatorParticle particle) {
		var set = ImmutableSet.<ValidatorParticle>builder()
			.addAll(validatorParticles)
			.add(particle)
			.build();

		return new AllValidators(set, stake, owners, delegationFlags, rakes);
	}

	public AllValidators remove(ValidatorParticle particle) {
		return new AllValidators(
			validatorParticles.stream()
				.filter(e -> !e.equals(particle))
				.collect(Collectors.toSet()),
			stake,
			owners,
			delegationFlags,
			rakes
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

	private int getRake(ECPublicKey validatorKey) {
		return ofNullable(rakes.get(validatorKey)).orElse(0);
	}

	public AllValidators setAllowDelegationFlag(ECPublicKey validatorKey, boolean allowDelegation) {
		var nextFlags = replaceEntry(Maps.immutableEntry(validatorKey, allowDelegation), delegationFlags);
		return new AllValidators(validatorParticles, stake, owners, nextFlags, rakes);
	}

	public AllValidators setOwner(ECPublicKey validatorKey, REAddr owner) {
		var nextOwners = replaceEntry(Maps.immutableEntry(validatorKey, owner), owners);
		return new AllValidators(validatorParticles, stake, nextOwners, delegationFlags, rakes);
	}

	public AllValidators setStake(ECPublicKey validatorKey, UInt256 amount) {
		var nextStake = replaceEntry(Maps.immutableEntry(validatorKey, amount), stake);
		return new AllValidators(validatorParticles, nextStake, owners, delegationFlags, rakes);
	}

	public AllValidators setRake(ECPublicKey validatorKey, int curRakePercentage) {
		var nextRakes = replaceEntry(Maps.immutableEntry(validatorKey, curRakePercentage), rakes);
		return new AllValidators(validatorParticles, stake, owners, delegationFlags, nextRakes);
	}

	// TODO: Remove add/remove from mainnet
	public AllValidators add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		var nextAmount = stake.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

		return new AllValidators(validatorParticles, nextStakedAmounts, owners, delegationFlags, rakes);
	}

	public AllValidators remove(ECPublicKey delegatedKey, UInt256 amount) {
		if (!this.stake.containsKey(delegatedKey)) {
			return this;
		}

		if (amount.isZero()) {
			return this;
		}

		final var oldAmount = this.stake.get(delegatedKey);
		final var comparison = amount.compareTo(oldAmount);

		if (comparison == 0) {
			// remove stake
			var nextStakedAmounts = removeKey(delegatedKey, stake);

			return new AllValidators(validatorParticles, nextStakedAmounts, owners, delegationFlags, rakes);
		} else if (comparison < 0) {
			// reduce stake
			var nextAmount = oldAmount.subtract(amount);
			var nextStakedAmounts = replaceEntry(Maps.immutableEntry(delegatedKey, nextAmount), stake);

			return new AllValidators(validatorParticles, nextStakedAmounts, owners, delegationFlags, rakes);
		}
		return this;
	}

	public <T> List<T> map(Function<ValidatorDetails, T> mapper) {
		return validatorParticles
			.stream()
			.map(this::fillDetails)
			.map(mapper::apply)
			.collect(Collectors.toList());
	}

	public long count() {
		return validatorParticles.size();
	}

	public <T> Optional<T> mapSingle(ECPublicKey validatorKey, Function<ValidatorDetails, T> mapper) {
		return validatorParticles.stream()
			.filter(particle -> particle.getValidatorKey().equals(validatorKey))
			.findFirst()
			.map(this::fillDetails)
			.map(mapper);
	}

	@Override
	public int hashCode() {
		return Objects.hash(validatorParticles, stake, owners, rakes, delegationFlags);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AllValidators)) {
			return false;
		}

		var other = (AllValidators) o;
		return Objects.equals(validatorParticles, other.validatorParticles)
			&& Objects.equals(stake, other.stake)
			&& Objects.equals(owners, other.owners)
			&& Objects.equals(rakes, other.rakes)
			&& Objects.equals(delegationFlags, other.delegationFlags);
	}

	private ValidatorDetails fillDetails(ValidatorParticle particle) {
		return ValidatorDetails.fromParticle(
			particle,
			getOwner(particle.getValidatorKey()),
			getStake(particle.getValidatorKey()),
			getOwnerStake(particle.getValidatorKey()),
			allowsDelegation(particle.getValidatorKey()),
			getRake(particle.getValidatorKey())
		);
	}
}
