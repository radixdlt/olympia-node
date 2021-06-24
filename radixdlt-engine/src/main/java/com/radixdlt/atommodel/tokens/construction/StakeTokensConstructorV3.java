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

package com.radixdlt.atommodel.tokens.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.atommodel.validators.state.AllowDelegationFlag;
import com.radixdlt.atommodel.validators.state.PreparedValidatorUpdate;
import com.radixdlt.atommodel.validators.state.ValidatorOwnerCopy;
import com.radixdlt.identifiers.REAddr;

import java.util.Optional;

public class StakeTokensConstructorV3 implements ActionConstructor<StakeTokens> {
	@Override
	public void construct(StakeTokens action, TxBuilder builder) throws TxBuilderException {
		builder.downFungible(
			TokensInAccount.class,
			p -> p.getResourceAddr().isNativeToken() && p.getHoldingAddr().equals(action.from()),
			amt -> new TokensInAccount(action.from(), amt, REAddr.ofNativeToken()),
			action.amount(),
			"Not enough balance for staking."
		);

		var flag = builder.read(
			AllowDelegationFlag.class,
			p -> p.getValidatorKey().equals(action.to()),
			Optional.of(new AllowDelegationFlag(action.to(), false)),
			"Could not find state"
		);

		if (!flag.allowsDelegation()) {
			var updateInFlight = builder
				.find(PreparedValidatorUpdate.class, p -> p.getValidatorKey().equals(action.to()));

			final REAddr owner;
			if (updateInFlight.isPresent()) {
				var validator = builder.read(
					PreparedValidatorUpdate.class,
					p -> p.getValidatorKey().equals(action.to()),
					Optional.empty(),
					"Could not find state"
				);
				owner = validator.getOwnerAddress();
			} else {
				var validator = builder.read(
					ValidatorOwnerCopy.class,
					p -> p.getValidatorKey().equals(action.to()),
					Optional.of(new ValidatorOwnerCopy(action.to(), REAddr.ofPubKeyAccount(action.to()))),
					"Could not find state"
				);
				owner = validator.getOwner();
			}
			if (!action.from().equals(owner)) {
				throw new TxBuilderException("Delegation flag is false and you are not the owner.");
			}
		}

		builder.up(new PreparedStake(action.amount(), action.from(), action.to()));
	}
}
