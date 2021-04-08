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
import com.radixdlt.atommodel.tokens.TokDefParticleFactory;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.utils.UInt256;

public final class SplitNativeToken implements TxAction {
	private final RRI rri;
	private final UInt256 minSize;

	public SplitNativeToken(RRI rri, UInt256 minSize) {
		this.rri = rri;
		this.minSize = minSize;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		var address = txBuilder.getAddressOrFail("Must have address");

		// HACK
		var factory = TokDefParticleFactory.create(
			rri,
			true
		);

		var substate = txBuilder.findSubstate(
			TransferrableTokensParticle.class,
			p -> p.getTokDefRef().equals(rri)
				&& p.getAddress().equals(address)
				&& p.getAmount().compareTo(minSize) > 0,
			"Could not find large particle greater than " + minSize
		);

		txBuilder.down(substate.getId());
		var particle = (TransferrableTokensParticle) substate.getParticle();
		var amt1 = particle.getAmount().divide(UInt256.TWO);
		var amt2 = particle.getAmount().subtract(amt1);
		txBuilder.up(factory.createTransferrable(address, amt1));
		txBuilder.up(factory.createTransferrable(address, amt2));
	}
}
