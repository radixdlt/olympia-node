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
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public final class MoveStake implements TxAction {
	private final ECPublicKey from;
	private final ECPublicKey to;
	private final UInt256 amount;
	private final REAddr accountAddr;

	public MoveStake(REAddr accountAddr, ECPublicKey from, ECPublicKey to, UInt256 amount) {
		this.accountAddr = Objects.requireNonNull(accountAddr);
		this.from = from;
		this.to = to;
		this.amount = amount;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swapFungible(
			StakedTokensParticle.class,
			p -> p.getOwner().equals(accountAddr) && p.getDelegateKey().equals(from),
			StakedTokensParticle::getAmount,
			amt -> new StakedTokensParticle(from, accountAddr, amt),
			amount,
			"Not enough staked."
		).with(amt -> new StakedTokensParticle(to, accountAddr, amt));
	}
}
