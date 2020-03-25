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
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hash;
import com.radixdlt.crypto.Signatures;

/**
 * Set of validators for consensus.
 * <p>
 * Note that this set will validate for set sizes less than 4,
 * as long as all validators sign.
 */
public final class ValidatorSet {
	private final ImmutableBiMap<ECPublicKey, Validator> validators;

	private ValidatorSet(Collection<Validator> validators) {
		this.validators = validators.stream()
			.collect(ImmutableBiMap.toImmutableBiMap(Validator::nodeKey, Function.identity()));
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
	 * Validate the specified message hash against the specified signatures.
	 *
	 * @param message The message hash to verify signatures against
	 * @param sigs The signatures to verify
	 * @return A {@link ValidationResult}
	 */
	public ValidationResult validate(Hash message, Signatures sigs) {
		final int threshold = threshold(this.validators.size());
		final ImmutableList<Validator> signed = sigs.signedMessage(message).stream()
			.map(this.validators::get)
			.filter(Objects::nonNull)
			.collect(ImmutableList.toImmutableList());
		if (signed.isEmpty() || signed.size() < threshold) {
			return ValidationResult.failure();
		} else {
			return ValidationResult.passed(signed);
		}
	}

	public ImmutableSet<Validator> getValidators() {
		return validators.values();
	}

	@VisibleForTesting
	static int threshold(int n) {
		return n - acceptableFaults(n);
	}

	@VisibleForTesting
	static int acceptableFaults(int n) {
		// Compute acceptable faults based on Byzantine limit n = 3f + 1
		// i.e. f = (n - 1) / 3
		return (n - 1) / 3;
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
