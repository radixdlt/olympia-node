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

import com.radixdlt.application.system.scrypt.Syscall;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokensInAccount;
import com.radixdlt.atomos.UnclaimedREAddr;
import com.radixdlt.identifiers.REAddr;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class CreateFixedTokenConstructor implements ActionConstructor<CreateFixedToken> {
	@Override
	public void construct(CreateFixedToken action, TxBuilder txBuilder) throws TxBuilderException {
		if (action.getResourceAddr().getType() != REAddr.REAddrType.HASHED_KEY) {
			throw new TxBuilderException("Invalid resource address.");
		}
		txBuilder.toLowLevelBuilder().syscall(Syscall.READDR_CLAIM, action.getSymbol().getBytes(StandardCharsets.UTF_8));
		txBuilder.down(
			UnclaimedREAddr.class,
			p -> p.getAddr().equals(action.getResourceAddr()),
			Optional.of(action.getResourceAddr()),
			() -> new TxBuilderException("RRI not available")
		);
		txBuilder.up(TokenResource.createFixedSupplyResource(action.getResourceAddr()));
		txBuilder.up(new TokensInAccount(action.getAccountAddr(), action.getResourceAddr(), action.getSupply()));
		txBuilder.up(new TokenResourceMetadata(
			action.getResourceAddr(),
			action.getName(),
			action.getDescription(),
			action.getIconUrl(),
			action.getTokenUrl()
		));
		txBuilder.end();
	}
}
