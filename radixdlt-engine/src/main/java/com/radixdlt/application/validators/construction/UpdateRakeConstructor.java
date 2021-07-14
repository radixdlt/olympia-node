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

package com.radixdlt.application.validators.construction;

import com.radixdlt.application.system.state.ValidatorStakeData;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.UpdateValidatorFee;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;

import java.util.Optional;
import java.util.OptionalLong;

public final class UpdateRakeConstructor implements ActionConstructor<UpdateValidatorFee> {
	private final long rakeIncreaseDebounceEpochLength;
	private final int maxRakeIncrease;

	public UpdateRakeConstructor(
		long rakeIncreaseDebounceEpochLength,
		int maxRakeIncrease
	) {
		this.rakeIncreaseDebounceEpochLength = rakeIncreaseDebounceEpochLength;
		this.maxRakeIncrease = maxRakeIncrease;
	}

	@Override
	public void construct(UpdateValidatorFee action, TxBuilder builder) throws TxBuilderException {
		builder.down(
			ValidatorRakeCopy.class,
			p -> p.getValidatorKey().equals(action.validatorKey()),
			Optional.of(action.validatorKey()),
			() -> new TxBuilderException("Cannot find state")
		);

		var curRakePercentage = builder.read(
			ValidatorStakeData.class,
			s -> s.getValidatorKey().equals(action.validatorKey()),
			Optional.of(action.validatorKey()),
			"Can't find validator stake"
		).getRakePercentage();

		var isIncrease = action.getFeePercentage() > curRakePercentage;
		var rakeIncrease = action.getFeePercentage() - curRakePercentage;
		if (isIncrease && rakeIncrease >= maxRakeIncrease) {
			throw new TxBuilderException("Max rake increase is " + maxRakeIncrease + " but trying to increase " + rakeIncrease);
		}

		var epochDiff = isIncrease ? rakeIncreaseDebounceEpochLength : 1;
		var curEpoch = builder.read(EpochData.class, p -> true, Optional.empty(), "Cannot find epoch");
		var epoch = curEpoch.getEpoch() + epochDiff;
		builder.up(new ValidatorRakeCopy(
			OptionalLong.of(epoch),
			action.validatorKey(),
			action.getFeePercentage()
		));
		builder.end();
	}
}
