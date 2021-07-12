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
import com.radixdlt.atom.SubstateTypeId;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.BurnToken;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;

import java.nio.ByteBuffer;

public final class BurnTokenConstructor implements ActionConstructor<BurnToken> {

	@Override
	public void construct(BurnToken action, TxBuilder txBuilder) throws TxBuilderException {
		if (action.amount().isZero()) {
			throw new TxBuilderException("Must transfer > 0.");
		}

		var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
		buf.put(SubstateTypeId.TOKENS.id());
		buf.put((byte) 0);
		buf.put(action.from().getBytes());

		var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
		var change = txBuilder.downFungible(
			index,
			p -> p.getResourceAddr().equals(action.resourceAddr())
				&& p.getHoldingAddr().equals(action.from()),
			action.amount(),
			() -> new TxBuilderException("Not enough balance for transfer.")
		);
		if (!change.isZero()) {
			txBuilder.up(new TokensInAccount(action.from(), action.resourceAddr(), change));
		}
		txBuilder.end();
	}
}
