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

package com.radixdlt.atommodel.validators.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.UpdateRake;
import com.radixdlt.atommodel.system.state.EpochData;
import com.radixdlt.atommodel.validators.scrypt.ValidatorConstraintScryptV2;
import com.radixdlt.atommodel.validators.state.RakeCopy;
import com.radixdlt.atommodel.validators.state.PreparedRakeUpdate;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.List;
import java.util.Optional;

import static com.radixdlt.atommodel.validators.state.PreparedRakeUpdate.RAKE_MAX;

public final class UpdateRakeConstructor implements ActionConstructor<UpdateRake> {
	@Override
	public void construct(UpdateRake action, TxBuilder builder) throws TxBuilderException {
		var epochData = builder.find(EpochData.class, p -> true).orElseThrow();

		var updateInFlight = builder
			.find(PreparedRakeUpdate.class, p -> p.getValidatorKey().equals(action.getValidatorKey()));
		if (updateInFlight.isPresent()) {
			builder.swap(
				PreparedRakeUpdate.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.empty(),
				"Cannot find state"
			).with(substateDown -> {
				var isIncrease = action.getRakePercentage() > substateDown.getCurRakePercentage();
				var epochDiff = isIncrease ? ValidatorConstraintScryptV2.RAKE_INCREASE_DEBOUNCE_EPOCH_LENGTH : 1;
				var epoch = epochData.getEpoch() + epochDiff;
				return List.of(new PreparedRakeUpdate(
					epoch,
					action.getValidatorKey(),
					substateDown.getCurRakePercentage(),
					action.getRakePercentage()
				));
			});
		} else {
			builder.swap(
				RakeCopy.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.of(SubstateWithArg.noArg(new RakeCopy(action.getValidatorKey(), RAKE_MAX))),
				"Cannot find state"
			).with(substateDown -> {
				var isIncrease = action.getRakePercentage() > substateDown.getCurRakePercentage();
				var epochDiff = isIncrease ? ValidatorConstraintScryptV2.RAKE_INCREASE_DEBOUNCE_EPOCH_LENGTH : 1;
				var epoch = epochData.getEpoch() + epochDiff;
				return List.of(new PreparedRakeUpdate(
					epoch,
					action.getValidatorKey(),
					substateDown.getCurRakePercentage(),
					action.getRakePercentage()
				));
			});
		}
	}
}
