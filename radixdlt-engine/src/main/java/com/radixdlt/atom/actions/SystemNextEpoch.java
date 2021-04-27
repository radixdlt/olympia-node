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

package com.radixdlt.atom.actions;

import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxErrorCode;
import com.radixdlt.atommodel.system.SystemParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.util.Optional;

public final class SystemNextEpoch implements TxAction {
	private final long timestamp;
	private final long currentEpoch;

	public SystemNextEpoch(long timestamp, long currentEpoch) {
		this.timestamp = timestamp;
		this.currentEpoch = currentEpoch;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.assertIsSystem("Not permitted as user to execute system next epoch");

		txBuilder.swap(
			SystemParticle.class,
			p -> p.getEpoch() == currentEpoch,
			currentEpoch == 0
				? Optional.of(SubstateWithArg.noArg(new SystemParticle(0, 0, 0)))
				: Optional.empty(),
			TxErrorCode.NO_SYSTEM_PARTICLE,
			"No System particle available"
		).with(substateDown -> new SystemParticle(substateDown.getEpoch() + 1, 0, timestamp));
	}
}
