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

package com.radixdlt.consensus.validators;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.utils.UInt128;
import com.radixdlt.utils.UInt256;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;

/**
 * Set of validators for consensus.
 * <p>
 * Note that this set will validate for set sizes less than 4,
 * as long as all validators sign.
 */
public final class ValidatorSet {
	// We assume that we won't have more than 2^128 validators (2^32 is in fact the limit)
	// in a single validator set so having this as the max power value will prevent overflows
	private static final UInt256 POWER_MAX_VALUE = UInt256.from(UInt128.MAX_VALUE);
	private final ImmutableBiMap<ECPublicKey, Validator> validators;
	private final transient UInt256 totalPower;

	private ValidatorSet(Collection<Validator> validators) {
		if (validators.stream().anyMatch(v -> v.getPower().compareTo(POWER_MAX_VALUE) > 0)) {
			throw new IllegalArgumentException("There exists a validator with power greater than " + POWER_MAX_VALUE);
		}

		this.validators = validators.stream()
			.collect(ImmutableBiMap.toImmutableBiMap(Validator::nodeKey, Function.identity()));
		this.totalPower = validators.stream()
			.map(Validator::getPower)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	/**
	 * Create a validator set from a collection of validators.
	 *
	 * @param validators the collection of validators
	 * @return The new {@code ValidatorSet}.
	 */
	public static ValidatorSet from(Collection<Validator> validators) {
		return new ValidatorSet(validators);
	}

	/**
	 * Create an initial validation state with no signatures for this validator set.
	 *
	 * @param anchor The hash to validate signatures against
	 * @return An initial validation state with no signatures
	 */
	public ValidationState newValidationState(Hash anchor) {
		return ValidationState.forValidatorSet(anchor, this);
	}

	public boolean containsKey(ECPublicKey key) {
		return validators.containsKey(key);
	}

	public UInt256 getPower(Set<ECPublicKey> signedKeys) {
		return signedKeys.stream()
			.map(validators::get)
			.map(Validator::getPower)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	public UInt256 getTotalPower() {
		return totalPower;
	}

	public ImmutableSet<Validator> getValidators() {
		return validators.values();
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(this.validators);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof ValidatorSet) {
			ValidatorSet other = (ValidatorSet) obj;
			return Objects.equals(this.validators, other.validators);
		}
		return false;
	}

	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), this.validators.keySet());
	}
}
