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
import com.radixdlt.atom.actions.UnstakeTokens;
import com.radixdlt.atommodel.system.state.StakeOwnership;
import com.radixdlt.atommodel.tokens.state.PreparedUnstakeOwned;

public class UnstakeTokensConstructorV2 implements ActionConstructor<UnstakeTokens> {
	@Override
	public void construct(UnstakeTokens action, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swapFungible(
			StakeOwnership.class,
			p -> p.getOwner().equals(action.accountAddr()) && p.getDelegateKey().equals(action.from()),
			amt -> new StakeOwnership(action.from(), action.accountAddr(), amt),
			action.amount(),
			"Not enough staked"
		).with(amt -> new PreparedUnstakeOwned(action.from(), action.accountAddr(), amt));
	}
}
