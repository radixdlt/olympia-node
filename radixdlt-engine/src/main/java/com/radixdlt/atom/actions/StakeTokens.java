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

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.TxErrorCode;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

public final class StakeTokens implements TxAction {
	private final REAddr fromAcct;
	private final ECPublicKey delegateKey;
	private final UInt256 amount;

	public StakeTokens(REAddr fromAcct, ECPublicKey delegateKey, UInt256 amount) {
		this.fromAcct = fromAcct;
		this.delegateKey = delegateKey;
		this.amount = amount;
	}

	public REAddr from() {
		return fromAcct;
	}

	public ECPublicKey to() {
		return delegateKey;
	}

	public UInt256 amount() {
		return amount;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		txBuilder.swapFungible(
			TokensParticle.class,
			p -> p.getResourceAddr().isNativeToken()
				&& p.getHoldingAddr().equals(fromAcct)
				&& (amount.compareTo(TokenUnitConversions.SUB_UNITS) < 0
				|| p.getAmount().compareTo(TokenUnitConversions.unitsToSubunits(1)) >= 0),
			TokensParticle::getAmount,
			amt -> new TokensParticle(fromAcct, amt, REAddr.ofNativeToken()),
			amount,
			TxErrorCode.INSUFFICIENT_FUNDS,
			"Not enough balance for staking."
		).with(amt -> new StakedTokensParticle(delegateKey, fromAcct, amt));
	}
}
