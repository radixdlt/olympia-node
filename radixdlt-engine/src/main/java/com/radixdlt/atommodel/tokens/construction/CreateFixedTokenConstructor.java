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

package com.radixdlt.atommodel.tokens.construction;

import com.radixdlt.atom.ActionConstructor;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atommodel.tokens.state.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.state.TokensParticle;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class CreateFixedTokenConstructor implements ActionConstructor<CreateFixedToken> {
	@Override
	public void construct(CreateFixedToken action, TxBuilder txBuilder) throws TxBuilderException {
		var addrParticle = new REAddrParticle(action.getResourceAddr());
		txBuilder.down(
			REAddrParticle.class,
			p -> p.getAddr().equals(action.getResourceAddr()),
			Optional.of(SubstateWithArg.withArg(addrParticle, action.getSymbol().getBytes(StandardCharsets.UTF_8))),
			"RRI not available"
		);
		txBuilder.up(new TokenDefinitionParticle(
			action.getResourceAddr(),
			action.getName(),
			action.getDescription(),
			action.getIconUrl(),
			action.getTokenUrl(),
			action.getSupply()
		));
		txBuilder.up(new TokensParticle(action.getAccountAddr(), action.getSupply(), action.getResourceAddr()));
	}
}
