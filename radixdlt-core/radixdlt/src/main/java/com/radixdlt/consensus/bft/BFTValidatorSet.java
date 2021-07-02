/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.consensus.bft;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.utils.UInt256;
import java.util.Collection;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Set of validators for consensus. Only validators with power >= 1 will
 * be part of the set.
 * <p>
 * Note that this set will validate for set sizes less than 4,
 * as long as all validators sign.
 */
public final class BFTValidatorSet {
	private final ImmutableBiMap<BFTNode, BFTValidator> validators;

	// Because we will base power on tokens and because tokens have a max limit
	// of 2^256 this should never overflow
	private final transient UInt256 totalPower;

	private BFTValidatorSet(Collection<BFTValidator> validators) {
		this(validators.stream());
	}

	private BFTValidatorSet(Stream<BFTValidator> validators) {
		this.validators = validators
			.filter(v -> !v.getPower().isZero())
			.collect(ImmutableBiMap.toImmutableBiMap(BFTValidator::getNode, Function.identity()));
		this.totalPower = this.validators.values().stream()
			.map(BFTValidator::getPower)
			.reduce(UInt256::add)
			.orElse(UInt256.ZERO);
	}

	/**
	 * Create a validator set from a collection of validators. The sum
	 * of power of all validator should not exceed UInt256.MAX_VALUE otherwise
	 * the resulting ValidatorSet will perform in an undefined way.
	 * This invariant should be upheld within the system due to max number of
	 * tokens being constrained to UInt256.MAX_VALUE.
	 *
	 * @param validators the collection of validators
	 * @return The new {@code ValidatorSet}.
	 */
	public static BFTValidatorSet from(Collection<BFTValidator> validators) {
		return new BFTValidatorSet(validators);
	}

	/**
	 * Create a validator set from a stream of validators. The sum
	 * of power of all validator should not exceed UInt256.MAX_VALUE otherwise
	 * the resulting ValidatorSet will perform in an undefined way.
	 * This invariant should be upheld within the system due to max number of
	 * tokens being constrained to UInt256.MAX_VALUE.
	 *
	 * @param validators the stream of validators
	 * @return The new {@code ValidatorSet}.
	 */
	public static BFTValidatorSet from(Stream<BFTValidator> validators) {
		return new BFTValidatorSet(validators);
	}

	/**
	 * Create an initial validation state with no signatures for this validator set.
	 *
	 * @return An initial validation state with no signatures
	 */
	public ValidationState newValidationState() {
		return ValidationState.forValidatorSet(this);
	}

	public boolean containsNode(BFTNode node) {
		return validators.containsKey(node);
	}

	public boolean containsNode(ECPublicKey publicKey) {
		return containsNode(BFTNode.create(publicKey));
	}

	public UInt256 getPower(BFTNode node) {
		return validators.get(node).getPower();
	}

	public UInt256 getPower(ECPublicKey publicKey) {
		return getPower(BFTNode.create(publicKey));
	}

	public UInt256 getTotalPower() {
		return totalPower;
	}

	public ImmutableSet<BFTValidator> getValidators() {
		return validators.values();
	}

	public ImmutableSet<BFTNode> nodes() {
		return validators.keySet();
	}

	public ImmutableMap<BFTNode, BFTValidator> validatorsByKey() {
		return validators;
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
		if (obj instanceof BFTValidatorSet) {
			BFTValidatorSet other = (BFTValidatorSet) obj;
			return Objects.equals(this.validators, other.validators);
		}
		return false;
	}

	@Override
	public String toString() {
		final StringJoiner joiner = new StringJoiner(",");
		for (BFTValidator validator : this.validators.values()) {
			joiner.add(String.format("%s=%s", validator.getNode().getSimpleName(), validator.getPower()));
		}
		return String.format("%s[%s]", this.getClass().getSimpleName(), joiner.toString());
	}
}
