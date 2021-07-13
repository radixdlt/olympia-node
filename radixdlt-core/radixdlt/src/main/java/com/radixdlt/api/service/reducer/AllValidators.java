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

import com.radixdlt.application.validators.state.ValidatorMetaData;
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

import static com.radixdlt.utils.functional.FunctionalUtils.addElement;
import static com.radixdlt.utils.functional.FunctionalUtils.mergeAll;
import static com.radixdlt.utils.functional.FunctionalUtils.newEntry;
import static com.radixdlt.utils.functional.FunctionalUtils.removeElement;
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
	private final Map<ECPublicKey, Integer> fees;
	private final Set<ECPublicKey> registered;

	private AllValidators(
		Set<ECPublicKey> registered,
		Map<ECPublicKey, UInt256> stake,
		Map<ECPublicKey, REAddr> owners,
		Map<ECPublicKey, Boolean> delegationFlags,
		Map<ECPublicKey, ValidatorMetaData> metadata,
		Map<ECPublicKey, Integer> fees
	) {
		this.registered = registered;
		this.stake = stake;
		this.owners = owners;
		this.delegationFlags = delegationFlags;
		this.metadata = metadata;
		this.fees = fees;
	}

	public static AllValidators create() {
		return new AllValidators(Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	public AllValidators set(ValidatorMetaData metaData) {
		var newMetadata = replaceEntry(newEntry(metaData.getValidatorKey(), metaData), metadata);
		return new AllValidators(
			registered,
			stake,
			owners,
			delegationFlags,
			newMetadata,
			fees
		);
	}

	public AllValidators setRegistered(ECPublicKey validatorKey, boolean isRegistered) {
		if (registered.contains(validatorKey) == isRegistered) {
			return this;
		}

		if (!isRegistered) {
			return new AllValidators(removeElement(validatorKey, registered), stake, owners, delegationFlags, metadata, fees);
		} else {
			return new AllValidators(addElement(validatorKey, registered), stake, owners, delegationFlags, metadata, fees);
		}
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
			.orElse(Boolean.FALSE);
	}

	public UInt256 getOwnerStake(ECPublicKey key) {
		return getOwner(key)
			.publicKey()
			.map(this::getStake)
			.orElse(UInt256.ZERO);
	}

	private int getRake(ECPublicKey validatorKey) {
		return ofNullable(fees.get(validatorKey)).orElse(0);
	}

	public AllValidators setAllowDelegationFlag(ECPublicKey validatorKey, boolean allowDelegation) {
		var nextFlags = replaceEntry(newEntry(validatorKey, allowDelegation), delegationFlags);
		return new AllValidators(registered, stake, owners, nextFlags, metadata, fees);
	}

	public AllValidators setOwner(ECPublicKey validatorKey, REAddr owner) {
		var nextOwners = replaceEntry(newEntry(validatorKey, owner), owners);
		return new AllValidators(registered, stake, nextOwners, delegationFlags, metadata, fees);
	}

	public AllValidators setStake(ECPublicKey delegatedKey, UInt256 amount) {
		var nextStake = replaceEntry(newEntry(delegatedKey, amount), stake);
		return new AllValidators(registered, nextStake, owners, delegationFlags, metadata, fees);
	}

	public AllValidators setRake(ECPublicKey validatorKey, int curRakePercentage) {
		var nextRakes = replaceEntry(newEntry(validatorKey, curRakePercentage), fees);
		return new AllValidators(registered, stake, owners, delegationFlags, metadata, nextRakes);
	}

	// TODO: Remove add/remove from mainnet
	public AllValidators add(ECPublicKey delegatedKey, UInt256 amount) {
		if (amount.isZero()) {
			return this;
		}

		var nextAmount = stake.getOrDefault(delegatedKey, UInt256.ZERO).add(amount);
		var nextStakedAmounts = replaceEntry(newEntry(delegatedKey, nextAmount), stake);

		return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, fees);
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

			return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, fees);
		} else if (comparison < 0) {
			// reduce stake
			var nextAmount = oldAmount.subtract(amount);
			var nextStakedAmounts = replaceEntry(newEntry(delegatedKey, nextAmount), stake);

			return new AllValidators(registered, nextStakedAmounts, owners, delegationFlags, metadata, fees);
		} else {
			throw new IllegalStateException("Removing stake which doesn't exist.");
		}
	}

	public <T> List<T> map(Function<ValidatorDetails, T> mapper) {
		return mergeAll(
			registered,
			metadata.keySet(),
			stake.keySet(),
			owners.keySet(),
			delegationFlags.keySet(),
			fees.keySet()
		).stream()
			.map(this::fillDetails)
			.map(mapper)
			.collect(Collectors.toList());
	}

	public long count() {
		return registered.size();
	}

	@Override
	public int hashCode() {
		return Objects.hash(registered, stake, owners, fees, delegationFlags, metadata);
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
			&& Objects.equals(fees, other.fees)
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
