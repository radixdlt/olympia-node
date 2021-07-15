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
import com.radixdlt.atom.actions.UnstakeOwnership;
import com.radixdlt.application.system.state.StakeOwnership;
import com.radixdlt.application.tokens.state.PreparedUnstakeOwnership;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;

import java.nio.ByteBuffer;

public class UnstakeOwnershipConstructor implements ActionConstructor<UnstakeOwnership> {
	@Override
	public void construct(UnstakeOwnership action, TxBuilder txBuilder) throws TxBuilderException {
		if (action.amount().isZero()) {
			throw new TxBuilderException("Must transfer > 0.");
		}

		var buf = ByteBuffer.allocate(2 + ECPublicKey.COMPRESSED_BYTES + (1 + ECPublicKey.COMPRESSED_BYTES));
		buf.put(SubstateTypeId.STAKE_OWNERSHIP.id());
		buf.put((byte) 0);
		buf.put(action.from().getCompressedBytes());
		buf.put(action.accountAddr().getBytes());
		if (buf.hasRemaining()) {
			// Sanity
			throw new IllegalStateException();
		}

		var index = SubstateIndex.create(buf.array(), StakeOwnership.class);
		var change = txBuilder.downFungible(
			index,
			p -> p.getOwner().equals(action.accountAddr()) && p.getDelegateKey().equals(action.from()),
			action.amount(),
			() -> new TxBuilderException("Not enough balance for transfer.")
		);
		if (!change.isZero()) {
			txBuilder.up(new StakeOwnership(action.from(), action.accountAddr(), change));
		}
		txBuilder.up(new PreparedUnstakeOwnership(action.from(), action.accountAddr(), action.amount()));
		txBuilder.end();
	}
}
