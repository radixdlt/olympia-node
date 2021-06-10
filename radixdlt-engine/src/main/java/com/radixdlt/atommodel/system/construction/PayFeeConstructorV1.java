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
import com.radixdlt.atom.actions.PayFee;
import com.radixdlt.atommodel.system.state.SystemParticle;
import com.radixdlt.atommodel.tokens.state.TokensInAccount;
import com.radixdlt.identifiers.REAddr;

public final class PayFeeConstructorV1 implements ActionConstructor<PayFee> {
	@Override
	public void construct(PayFee payFee, TxBuilder txBuilder) throws TxBuilderException {
		var epoch = txBuilder.find(SystemParticle.class, p -> true)
			.map(SystemParticle::getEpoch).orElse(0L);
		txBuilder.deallocateFungible(
			TokensInAccount.class,
			p -> p.getResourceAddr().isNativeToken()
				&& p.getHoldingAddr().equals(payFee.from())
				&& p.getEpochUnlocked().map(e -> e <= epoch).orElse(true),
			amt -> new TokensInAccount(payFee.from(), amt, REAddr.ofNativeToken()),
			payFee.amount(),
			"Not enough balance to for fee burn."
		);
	}
}
