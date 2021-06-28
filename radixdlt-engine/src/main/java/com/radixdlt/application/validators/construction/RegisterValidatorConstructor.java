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
import com.radixdlt.atom.actions.RegisterValidator;
import com.radixdlt.application.validators.state.PreparedRegisteredUpdate;
import com.radixdlt.application.validators.state.ValidatorRegisteredCopy;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.List;
import java.util.Optional;

public class RegisterValidatorConstructor implements ActionConstructor<RegisterValidator> {
	@Override
	public void construct(RegisterValidator action, TxBuilder txBuilder) throws TxBuilderException {
		var updateInFlight = txBuilder
			.find(PreparedRegisteredUpdate.class, p -> p.getValidatorKey().equals(action.validatorKey()));
		if (updateInFlight.isPresent()) {
			txBuilder.swap(
				PreparedRegisteredUpdate.class,
				p -> p.getValidatorKey().equals(action.validatorKey()),
				Optional.empty(),
				"Cannot find state"
			).with(substateDown -> List.of(new PreparedRegisteredUpdate(action.validatorKey(), true)));
		} else {
			txBuilder.swap(
				ValidatorRegisteredCopy.class,
				p -> p.getValidatorKey().equals(action.validatorKey()),
				Optional.of(SubstateWithArg.noArg(new ValidatorRegisteredCopy(action.validatorKey(), false))),
				"Cannot find state"

			).with(substateDown -> List.of(new PreparedRegisteredUpdate(action.validatorKey(), true)));
		}
	}
}
