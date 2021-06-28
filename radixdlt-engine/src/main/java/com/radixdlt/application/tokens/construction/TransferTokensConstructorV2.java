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

package com.radixdlt.application.tokens.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.TransferToken;
import com.radixdlt.application.tokens.state.TokensInAccount;

public class TransferTokensConstructorV2 implements ActionConstructor<TransferToken> {
	@Override
	public void construct(TransferToken action, TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swapFungible(
			TokensInAccount.class,
			p -> p.getResourceAddr().equals(action.resourceAddr())
				&& p.getHoldingAddr().equals(action.from()),
			amt -> new TokensInAccount(action.from(), action.resourceAddr(), amt),
			action.amount(),
			() -> new TxBuilderException("Not enough balance for transfer.")
		).with(amt -> new TokensInAccount(action.to(), action.resourceAddr(), action.amount()));
		txBuilder.end();
	}
}
