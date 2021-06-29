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

import com.radixdlt.atommodel.tokens.ResourceInBucket;
import com.radixdlt.atommodel.tokens.Bucket;
import com.radixdlt.atommodel.tokens.state.ExittingStake;
import com.radixdlt.constraintmachine.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

import java.util.Objects;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class ValidatorStakeData implements ResourceInBucket {
	private final UInt256 totalStake;
	private final UInt256 totalOwnership;
	private final int rakePercentage;
	private final REAddr ownerAddr;
	private final boolean isRegistered;

	// Bucket keys
	private final ECPublicKey validatorKey;

	private ValidatorStakeData(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership,
		int rakePercentage,
		REAddr ownerAddr,
		boolean isRegistered
	) {
		if (totalStake.isZero() != totalOwnership.isZero()) {
			throw new IllegalArgumentException(
				"Zero must be equivalent between stake and ownership: " + totalStake + " " + totalOwnership
			);
		}
		this.validatorKey = Objects.requireNonNull(validatorKey);
		this.totalStake = totalStake;
		this.totalOwnership = totalOwnership;
		this.rakePercentage = rakePercentage;
		this.ownerAddr = ownerAddr;
		this.isRegistered = isRegistered;
	}

	public static ValidatorStakeData createVirtual(ECPublicKey validatorKey) {
		return new ValidatorStakeData(validatorKey, UInt256.ZERO, UInt256.ZERO, RAKE_MAX, REAddr.ofPubKeyAccount(validatorKey), false);
	}

	public static ValidatorStakeData create(
		ECPublicKey validatorKey,
		UInt256 totalStake,
		UInt256 totalOwnership,
		int rakePercentage,
		REAddr ownerAddress,
		boolean isRegistered
	) {
		return new ValidatorStakeData(
			validatorKey,
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddress,
			isRegistered
		);
	}

	public boolean isRegistered() {
		return isRegistered;
	}

	public REAddr getOwnerAddr() {
		return ownerAddr;
	}

	public int getRakePercentage() {
		return rakePercentage;
	}

	public ValidatorStakeData setRegistered(boolean isRegistered) {
		return new ValidatorStakeData(
			validatorKey,
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}

	public ValidatorStakeData setRakePercentage(int rakePercentage) {
		return new ValidatorStakeData(
			validatorKey,
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}

	public ValidatorStakeData setOwnerAddr(REAddr ownerAddr) {
		return new ValidatorStakeData(
			validatorKey,
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}

	@Override
	public UInt256 getAmount() {
		return this.totalStake;
	}

	@Override
	public Bucket bucket() {
		return new StakeBucket(validatorKey);
	}

	public ValidatorStakeData addEmission(UInt256 amount) {
		return new ValidatorStakeData(
			validatorKey,
			this.totalStake.add(amount),
			totalOwnership,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}

	public Pair<ValidatorStakeData, StakeOwnership> stake(REAddr owner, UInt256 stake) throws ProcedureException {
		if (totalStake.isZero()) {
			var nextValidatorStake = new ValidatorStakeData(validatorKey, stake, stake, rakePercentage, ownerAddr, isRegistered);
			var stakeOwnership = new StakeOwnership(validatorKey, owner, stake);
			return Pair.of(nextValidatorStake, stakeOwnership);
		}

		var ownership384 = UInt384.from(totalOwnership).multiply(stake).divide(totalStake);
		if (ownership384.isHighBitSet()) {
			throw new IllegalStateException("Overflow");
		}
		var ownershipAmt = ownership384.getLow();
		var stakeOwnership = new StakeOwnership(validatorKey, owner, ownershipAmt);
		var nextValidatorStake = new ValidatorStakeData(
			validatorKey,
			totalStake.add(stake),
			totalOwnership.add(ownershipAmt),
			rakePercentage,
			ownerAddr,
			isRegistered
		);
		return Pair.of(nextValidatorStake, stakeOwnership);
	}

	public Pair<ValidatorStakeData, ExittingStake> unstakeOwnership(REAddr owner, UInt256 unstakeOwnership, long epochUnlocked) {
		if (totalOwnership.compareTo(unstakeOwnership) < 0) {
			throw new IllegalStateException("Not enough ownership");
		}

		var unstaked384 = UInt384.from(totalStake).multiply(unstakeOwnership).divide(totalOwnership);
		if (unstaked384.isHighBitSet()) {
			throw new IllegalStateException("Overflow");
		}
		var unstaked = unstaked384.getLow();
		var nextValidatorStake = new ValidatorStakeData(
			validatorKey,
			totalStake.subtract(unstaked),
			totalOwnership.subtract(unstakeOwnership),
			rakePercentage,
			ownerAddr,
			isRegistered
		);
		var exittingStake = new ExittingStake(validatorKey, owner, epochUnlocked, unstaked);
		return Pair.of(nextValidatorStake, exittingStake);
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	@Override
	public String toString() {
		return String.format("%s{stake=%s ownership=%s rake=%s owner=%s}",
			getClass().getSimpleName(),
			totalStake,
			totalOwnership,
			rakePercentage,
			ownerAddr
		);
	}

	public UInt256 getTotalOwnership() {
		return this.totalOwnership;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ValidatorStakeData)) {
			return false;
		}
		var that = (ValidatorStakeData) o;
		return Objects.equals(validatorKey, that.validatorKey)
			&& Objects.equals(totalOwnership, that.totalOwnership)
			&& Objects.equals(totalStake, that.totalStake)
			&& Objects.equals(rakePercentage, that.rakePercentage)
			&& Objects.equals(ownerAddr, that.ownerAddr)
			&& this.isRegistered == that.isRegistered;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			validatorKey,
			totalOwnership,
			totalStake,
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}
}
