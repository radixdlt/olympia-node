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
import com.radixdlt.atommodel.validators.state.NoValidatorUpdate;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.List;
import java.util.Optional;

public final class UpdateRakeConstructor implements ActionConstructor<UpdateRake> {
	@Override
	public void construct(UpdateRake action, TxBuilder builder) throws TxBuilderException {
		var epochData = builder.find(EpochData.class, p -> true).orElseThrow();

		builder.swap(
			NoValidatorUpdate.class,
			p -> p.getValidatorKey().equals(action.getValidatorKey()),
			Optional.of(SubstateWithArg.noArg(new NoValidatorUpdate(action.getValidatorKey(), 0))),
			"Already a validator"
		).with(
			substateDown -> List.of(new PreparedValidatorUpdate(
				epochData.getEpoch() + ValidatorConstraintScryptV2.RAKE_UPDATE_DEBOUNCE_EPOCH_LENGTH,
				action.getValidatorKey(),
				action.getRakePercentage()
			))
		);
	}
}
