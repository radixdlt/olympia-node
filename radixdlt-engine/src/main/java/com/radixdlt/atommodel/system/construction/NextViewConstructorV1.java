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

package com.radixdlt.atommodel.system.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.SystemNextView;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.List;
import java.util.Optional;

public class NextViewConstructorV1 implements ActionConstructor<SystemNextView> {
	@Override
	public void construct(SystemNextView action, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swap(
			SystemParticle.class,
			p -> true,
			Optional.of(SubstateWithArg.noArg(new SystemParticle(0, 0, 0))),
			"No System particle available"
		).with(substateDown -> {
			if (action.view() <= substateDown.getView()) {
				throw new TxBuilderException("Next view isn't higher than current view.");
			}
			return List.of(new SystemParticle(substateDown.getEpoch(), action.view(), action.timestamp()));
		});
	}
}
