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
import com.radixdlt.atommodel.system.state.RoundData;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.system.state.ValidatorBFTData;
import com.radixdlt.atommodel.system.state.ValidatorStakeData;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.crypto.ECPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

public class NextViewConstructorV3 implements ActionConstructor<SystemNextView> {
	@Override
	public void construct(SystemNextView action, TxBuilder txBuilder) throws TxBuilderException {
		var curLeader = action.leaderMapping().apply(action.view());
		var prevRound = txBuilder.down(
			RoundData.class,
			p -> true,
			Optional.of(SubstateWithArg.noArg(new RoundData(0, 0))),
			"No round state available."
		);

		if (action.view() <= prevRound.getView()) {
			throw new TxBuilderException("Next view: " + action + " isn't higher than current view: " + prevRound);
		}

		var validatorsToUpdate = new TreeMap<ECPublicKey, ValidatorBFTData>(
			(o1, o2) -> Arrays.compare(o1.getBytes(), o2.getBytes())
		);
		for (long view = prevRound.getView() + 1; view < action.view(); view++) {
			var missingLeader = action.leaderMapping().apply(view);
			if (!validatorsToUpdate.containsKey(missingLeader)) {
				var validatorData = txBuilder.down(
					ValidatorBFTData.class,
					p -> p.validatorKey().equals(missingLeader),
					"Could not find validator"
				);
				validatorsToUpdate.put(missingLeader, validatorData);
			}
			var nextData = validatorsToUpdate.get(missingLeader).incrementProposalsMissed();
			validatorsToUpdate.put(missingLeader, nextData);
		}

		if (!validatorsToUpdate.containsKey(curLeader)) {
			var validatorData = txBuilder.down(
				ValidatorBFTData.class,
				p -> p.validatorKey().equals(curLeader),
				"Could not find validator"
			);
			validatorsToUpdate.put(curLeader, validatorData);
		}
		var nextData = validatorsToUpdate.get(curLeader).incrementCompletedProposals();
		validatorsToUpdate.put(curLeader, nextData);

		for (var e : validatorsToUpdate.entrySet()) {
			txBuilder.up(e.getValue());
		}

		txBuilder.up(new RoundData(action.view(), action.timestamp()));
	}
}
