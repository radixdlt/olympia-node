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
import com.radixdlt.atom.actions.UpdateValidatorOwnerAddress;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.identifiers.REAddr;

import java.util.List;
import java.util.Optional;

public class UpdateValidatorOwnerConstructor implements ActionConstructor<UpdateValidatorOwnerAddress> {
	@Override
	public void construct(UpdateValidatorOwnerAddress action, TxBuilder builder) throws TxBuilderException {
		var updateInFlight = builder
			.find(PreparedValidatorUpdate.class, p -> p.getValidatorKey().equals(action.getValidatorKey()));

		if (updateInFlight.isPresent()) {
			builder.swap(
				PreparedValidatorUpdate.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.empty(),
				"Cannot find state"
			).with(substateDown ->
				List.of(new PreparedValidatorUpdate(
					action.getValidatorKey(),
					action.getOwnerAddress()
				))
			);
		} else {
			builder.swap(
				ValidatorOwnerCopy.class,
				p -> p.getValidatorKey().equals(action.getValidatorKey()),
				Optional.of(SubstateWithArg.noArg(
					new ValidatorOwnerCopy(action.getValidatorKey(), REAddr.ofPubKeyAccount(action.getValidatorKey()))
				)),
				"Cannot find state"
			).with(substateDown ->
				List.of(new PreparedValidatorUpdate(
					action.getValidatorKey(),
					action.getOwnerAddress()
				))
			);
		}
	}
}
