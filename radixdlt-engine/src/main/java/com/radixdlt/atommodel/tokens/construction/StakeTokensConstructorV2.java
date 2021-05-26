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

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.atommodel.tokens.state.PreparedStake;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.identifiers.REAddr;

public class StakeTokensConstructorV2 implements ActionConstructor<StakeTokens> {
	@Override
	public void construct(StakeTokens action, TxBuilder builder) throws TxBuilderException {
		builder.swapFungible(
			TokensParticle.class,
			p -> p.getResourceAddr().isNativeToken()
				&& p.getHoldingAddr().equals(action.from())
				&& (action.amount().compareTo(TokenUnitConversions.SUB_UNITS) < 0
				|| p.getAmount().compareTo(TokenUnitConversions.unitsToSubunits(1)) >= 0),
			amt -> new TokensParticle(action.from(), amt, REAddr.ofNativeToken()),
			action.amount(),
			"Not enough balance for staking."
		).with(amt -> new PreparedStake(amt, action.from(), action.to()));
	}
}
