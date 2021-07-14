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
import com.radixdlt.atom.actions.StakeTokens;
import com.radixdlt.application.tokens.state.PreparedStake;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.application.validators.state.AllowDelegationFlag;
import com.radixdlt.application.validators.state.ValidatorOwnerCopy;
import com.radixdlt.constraintmachine.SubstateIndex;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.nio.ByteBuffer;
import java.util.Optional;

public class StakeTokensConstructorV3 implements ActionConstructor<StakeTokens> {
	private final UInt256 minimumStake;

	public StakeTokensConstructorV3(UInt256 minimumStake) {
		this.minimumStake = minimumStake;
	}

	@Override
	public void construct(StakeTokens action, TxBuilder builder) throws TxBuilderException {
		if (action.amount().compareTo(minimumStake) < 0) {
			throw new TxBuilderException("Minimum to stake is " + minimumStake + " but trying to stake " + action.amount());
		}

		var buf = ByteBuffer.allocate(2 + 1 + ECPublicKey.COMPRESSED_BYTES);
		buf.put(SubstateTypeId.TOKENS.id());
		buf.put((byte) 0);
		buf.put(action.from().getBytes());

		var index = SubstateIndex.create(buf.array(), TokensInAccount.class);
		var change = builder.downFungible(
			index,
			p -> p.getResourceAddr().isNativeToken()
				&& p.getHoldingAddr().equals(action.from()),
			action.amount(),
			() -> new TxBuilderException("Not enough balance for transfer.")
		);
		if (!change.isZero()) {
			builder.up(new TokensInAccount(action.from(), REAddr.ofNativeToken(), change));
		}

		var flag = builder.read(
			AllowDelegationFlag.class,
			p -> p.getValidatorKey().equals(action.to()),
			Optional.of(action.to()),
			"Could not find state"
		);

		if (!flag.allowsDelegation()) {
			final REAddr owner;
			var validator = builder.read(
				ValidatorOwnerCopy.class,
				p -> p.getValidatorKey().equals(action.to()),
				Optional.of(action.to()),
				"Could not find state"
			);
			owner = validator.getOwner();
			if (!action.from().equals(owner)) {
				throw new TxBuilderException("Delegation flag is false and you are not the owner.");
			}
		}
		builder.up(new PreparedStake(action.amount(), action.from(), action.to()));
		builder.end();
	}
}
