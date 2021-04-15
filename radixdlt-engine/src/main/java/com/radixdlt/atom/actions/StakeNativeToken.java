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
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atomos.RriId;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

public final class StakeNativeToken implements TxAction {
	private final RRI nativeToken;
	private final RadixAddress delegateAddress;
	private final UInt256 amount;

	public StakeNativeToken(RRI nativeToken, RadixAddress delegateAddress, UInt256 amount) {
		this.nativeToken = nativeToken;
		this.delegateAddress = delegateAddress;
		this.amount = amount;
	}

	public RRI rri() {
		return nativeToken;
	}

	public RadixAddress to() {
		return delegateAddress;
	}

	public UInt256 amount() {
		return amount;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		var address = txBuilder.getAddressOrFail("Must have an address.");
		txBuilder.swapFungible(
			TokensParticle.class,
			p -> p.getRriId().isNativeToken()
				&& p.getAddress().equals(address)
				&& (amount.compareTo(TokenUnitConversions.SUB_UNITS) < 0
				|| p.getAmount().compareTo(TokenUnitConversions.unitsToSubunits(1)) >= 0),
			TokensParticle::getAmount,
			amt -> new TokensParticle(address, amt, RriId.nativeToken()),
			amount,
			"Not enough balance for staking."
		).with(amt -> new StakedTokensParticle(delegateAddress, address, amt));
	}
}
