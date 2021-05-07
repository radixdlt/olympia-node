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
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

public final class UnstakeTokens implements TxAction {
	private final REAddr accountAddr;
	private final ECPublicKey delegateAddress;
	private final UInt256 amount;

	public UnstakeTokens(REAddr accountAddr, ECPublicKey delegateAddress, UInt256 amount) {
		this.accountAddr = accountAddr;
		this.delegateAddress = delegateAddress;
		this.amount = amount;
	}

	public REAddr accountAddr() {
		return accountAddr;
	}

	public ECPublicKey from() {
		return delegateAddress;
	}

	public UInt256 amount() {
		return amount;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swapFungible(
			StakedTokensParticle.class,
			p -> p.getOwner().equals(accountAddr) && p.getDelegateKey().equals(delegateAddress),
			amt -> new StakedTokensParticle(amt, accountAddr, delegateAddress),
			amount,
			"Not enough staked."
		).with(amt -> new TokensParticle(accountAddr, amt, REAddr.ofNativeToken()));
	}
}
