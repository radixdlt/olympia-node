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

package com.radixdlt.application.system.scrypt;

import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.application.tokens.state.ExittingStake;
import com.radixdlt.constraintmachine.exceptions.ProcedureException;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;

public final class ValidatorScratchPad {
	private final ECPublicKey validatorKey;
	private UInt384 totalStake;
	private UInt384 totalOwnership;
	private int rakePercentage;
	private REAddr ownerAddr;
	private boolean isRegistered;

	public ValidatorScratchPad(ValidatorStakeData validatorStakeData) {
		this.totalStake = UInt384.from(validatorStakeData.getTotalStake());
		this.totalOwnership = UInt384.from(validatorStakeData.getTotalOwnership());
		this.rakePercentage = validatorStakeData.getRakePercentage();
		this.ownerAddr = validatorStakeData.getOwnerAddr();
		this.isRegistered = validatorStakeData.isRegistered();
		this.validatorKey = validatorStakeData.getValidatorKey();
	}

	public ECPublicKey getValidatorKey() {
		return validatorKey;
	}

	public REAddr getOwnerAddr() {
		return ownerAddr;
	}

	public int getRakePercentage() {
		return rakePercentage;
	}

	public void setRegistered(boolean isRegistered) {
		this.isRegistered = isRegistered;
	}

	public void setRakePercentage(int rakePercentage) {
		this.rakePercentage = rakePercentage;
	}

	public void setOwnerAddr(REAddr ownerAddr) {
		this.ownerAddr = ownerAddr;
	}

	public void addEmission(UInt256 amount) {
		this.totalStake = verifyNoOverflow(this.totalStake.add(amount));
	}

	private static UInt384 verifyNoOverflow(UInt384 i) {
		if (!i.getHigh().isZero()) {
			throw new IllegalStateException("Unexpected overflow occurred " + i);
		}
		return i;
	}

	private static UInt256 toSafeLow(UInt384 i) {
		return verifyNoOverflow(i).getLow();
	}

	public StakeOwnership stake(REAddr owner, UInt256 stake) throws ProcedureException {
		if (totalStake.isZero()) {
			this.totalStake = UInt384.from(stake);
			this.totalOwnership = this.totalStake;
			return new StakeOwnership(validatorKey, owner, this.totalOwnership.getLow());
		}

		var ownership384 = totalOwnership.multiply(stake).divide(totalStake);
		var ownershipAmt = toSafeLow(ownership384);
		this.totalStake = verifyNoOverflow(this.totalStake.add(stake));
		this.totalOwnership = verifyNoOverflow(this.totalOwnership.add(ownershipAmt));

		return new StakeOwnership(validatorKey, owner, ownershipAmt);
	}

	public ExittingStake unstakeOwnership(REAddr owner, UInt256 unstakeOwnership, long epochUnlocked) {
		if (totalOwnership.getLow().compareTo(unstakeOwnership) < 0) {
			throw new IllegalStateException("Not enough ownership");
		}

		var unstaked384 = totalStake.multiply(unstakeOwnership).divide(totalOwnership);
		var unstaked = toSafeLow(unstaked384);
		this.totalStake = this.totalStake.subtract(unstaked);
		this.totalOwnership = this.totalOwnership.subtract(unstakeOwnership);
		return new ExittingStake(epochUnlocked, validatorKey, owner, unstaked);
	}

	public ValidatorStakeData toSubstate() {
		return ValidatorStakeData.create(
			validatorKey,
			totalStake.getLow(),
			totalOwnership.getLow(),
			rakePercentage,
			ownerAddr,
			isRegistered
		);
	}
}
