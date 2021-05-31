/*
 * (C) Copyright 2021 Radix DLT Ltd
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
 *
 */

package com.radixdlt.atommodel.system.state;

import com.radixdlt.atommodel.system.scrypt.SystemConstraintScryptV2;
import com.radixdlt.atommodel.tokens.TokenDefinitionUtils;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Objects;

public final class ValidatorStake implements Particle {
	public static final UInt256 MINIMUM_STAKE = TokenDefinitionUtils.SUB_UNITS.multiply(UInt256.TEN);

	private final UInt256 totalStake;
	private final UInt256 totalOwnership;

	// Bucket keys
	private final ECPublicKey validatorKey;

	private ValidatorStake(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership
	) {
		if (totalStake.isZero() != totalOwnership.isZero()) {
			throw new IllegalArgumentException(
				"Zero must be equivalent between stake and ownership: " + totalStake + " " + totalOwnership
			);
		}
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.totalStake = totalStake;
		this.totalOwnership = totalOwnership;
	}

	public static ValidatorStake create(ECPublicKey validatorKey) {
		return new ValidatorStake(validatorKey, UInt256.ZERO, UInt256.ZERO);
	}

	public static ValidatorStake create(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership
	) {
		return new ValidatorStake(validatorKey, totalStake, totalOwnership);
	}

	public ValidatorStake addEmission(UInt256 amount) {
		return new ValidatorStake(
			validatorKey,
			this.totalStake.add(amount),
			totalOwnership
		);
	}

	public Pair<ValidatorStake, StakeOwnership> stake(REAddr owner, UInt256 stake) throws ProcedureException {
		if (stake.compareTo(MINIMUM_STAKE) < 0) {
			throw new ProcedureException("Trying to stake " + stake + " but minimum stake is " + MINIMUM_STAKE);
		}

		if (totalStake.isZero()) {
			var nextValidatorStake = new ValidatorStake(validatorKey, stake, stake);
			var stakeOwnership = new StakeOwnership(validatorKey, owner, stake);
			return Pair.of(nextValidatorStake, stakeOwnership);
		}

		var ownership384 = UInt384.from(totalOwnership).multiply(stake).divide(totalStake);
		if (ownership384.isHighBitSet()) {
			throw new IllegalStateException("Overflow");
		}
		var ownershipAmt = ownership384.getLow();
		var stakeOwnership = new StakeOwnership(validatorKey, owner, ownershipAmt);
		var nextValidatorStake = new ValidatorStake(validatorKey, totalStake.add(stake), totalOwnership.add(ownershipAmt));
		return Pair.of(nextValidatorStake, stakeOwnership);
	}

	public Pair<ValidatorStake, ExittingStake> unstakeOwnership(REAddr owner, UInt256 unstakeOwnership, long curEpoch) {
		if (totalOwnership.compareTo(unstakeOwnership) < 0) {
			throw new IllegalStateException("Not enough ownership");
		}

		var unstaked384 = UInt384.from(totalStake).multiply(unstakeOwnership).divide(totalOwnership);
		if (unstaked384.isHighBitSet()) {
			throw new IllegalStateException("Overflow");
		}
		var unstaked = unstaked384.getLow();
		var nextValidatorStake = new ValidatorStake(validatorKey, totalStake.subtract(unstaked), totalOwnership.subtract(unstakeOwnership));
		var epochUnlocked = curEpoch + SystemConstraintScryptV2.EPOCHS_LOCKED;
		var exittingStake = new ExittingStake(validatorKey, owner, epochUnlocked, unstaked);
		return Pair.of(nextValidatorStake, exittingStake);
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	@Override
	public String toString() {
		return String.format("%s[%s:%s]",
			getClass().getSimpleName(),
			totalStake,
			validatorKey
		);
	}

	public UInt256 getTotalOwnership() {
		return this.totalOwnership;
	}

	public UInt256 getTotalStake() {
		return this.totalStake;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValidatorStake)) {
			return false;
		}
		var that = (ValidatorStake) o;
		return Objects.equals(validatorKey, that.validatorKey)
			&& Objects.equals(totalOwnership, that.totalOwnership)
			&& Objects.equals(totalStake, that.totalStake);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			validatorKey,
			totalOwnership,
			totalStake
		);
	}
}
