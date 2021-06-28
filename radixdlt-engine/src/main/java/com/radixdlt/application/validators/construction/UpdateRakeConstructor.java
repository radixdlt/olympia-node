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

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.UpdateRake;
import com.radixdlt.application.system.state.EpochData;
import com.radixdlt.application.validators.state.ValidatorRakeCopy;
import com.radixdlt.application.validators.state.PreparedRakeUpdate;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.Optional;

import static com.radixdlt.application.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class UpdateRakeConstructor implements ActionConstructor<UpdateRake> {
	private final long rakeIncreaseDebounceEpochLength;

	public UpdateRakeConstructor(long rakeIncreaseDebounceEpochLength) {
		this.rakeIncreaseDebounceEpochLength = rakeIncreaseDebounceEpochLength;
	}

	@Override
	public void construct(UpdateRake action, TxBuilder builder) throws TxBuilderException {
		var updateInFlight = builder
			.find(PreparedRakeUpdate.class, p -> p.getValidatorKey().equals(action.getValidatorKey()));
		final int curRakePercentage;
		if (updateInFlight.isPresent()) {
			curRakePercentage = builder.down(
				PreparedRakeUpdate.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.empty(),
				"Cannot find state"
			).getCurRakePercentage();
		} else {
			curRakePercentage = builder.down(
				ValidatorRakeCopy.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.of(SubstateWithArg.noArg(new ValidatorRakeCopy(action.getValidatorKey(), RAKE_MAX))),
				"Cannot find state"
			).getCurRakePercentage();
		}

		var curEpoch = builder.read(EpochData.class, p -> true, Optional.empty(), "Cannot find epoch");
		var isIncrease = action.getRakePercentage() > curRakePercentage;
		var epochDiff = isIncrease ? rakeIncreaseDebounceEpochLength : 1;
		var epoch = curEpoch.getEpoch() + epochDiff;
		builder.up(new PreparedRakeUpdate(
			epoch,
			action.getValidatorKey(),
			curRakePercentage,
			action.getRakePercentage()
		));
		builder.end();
	}
}
