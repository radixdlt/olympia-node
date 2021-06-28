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
import com.radixdlt.atom.actions.SplitToken;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.utils.UInt256;

public final class SplitTokenConstructor implements ActionConstructor<SplitToken> {
	@Override
	public void construct(SplitToken action, TxBuilder txBuilder) throws TxBuilderException {
		var userAccount = action.userAcct();
		var tokens = txBuilder.downSubstate(
			TokensInAccount.class,
			p -> p.getResourceAddr().equals(action.rri())
				&& p.getHoldingAddr().equals(userAccount)
				&& p.getAmount().compareTo(action.minSize()) > 0,
			"Could not find large particle greater than " + action.minSize()
		);

		var amt1 = tokens.getAmount().divide(UInt256.TWO);
		var amt2 = tokens.getAmount().subtract(amt1);
		txBuilder.up(new TokensInAccount(userAccount, amt1, action.rri()));
		txBuilder.up(new TokensInAccount(userAccount, amt2, action.rri()));
	}
}
