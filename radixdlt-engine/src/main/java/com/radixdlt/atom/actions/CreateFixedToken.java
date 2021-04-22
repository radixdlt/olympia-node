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

import com.radixdlt.atom.FixedTokenDefinition;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokensParticle;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class CreateFixedToken implements TxAction {
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;
	private final UInt256 supply;

	public CreateFixedToken(FixedTokenDefinition def) {
		this(def.getSymbol(), def.getName(), def.getDescription(), def.getIconUrl(), def.getTokenUrl(), def.getSupply());
	}

	public CreateFixedToken(
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl,
		UInt256 supply
	) {
		this.symbol = Objects.requireNonNull(symbol);
		this.name = Objects.requireNonNull(name);
		this.description = description;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
		this.supply = Objects.requireNonNull(supply);
	}

	public UInt256 getSupply() {
		return supply;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description == null ? "" : description;
	}

	public String getIconUrl() {
		return iconUrl == null ? "" : iconUrl;
	}

	public String getTokenUrl() {
		return tokenUrl == null ? "" : tokenUrl;
	}

	@Override
	public void execute(TxBuilder txBuilder) throws TxBuilderException {
		final var address = txBuilder.getAddressOrFail("Required address for fixed token.");
		final var tokenAddress = REAddr.ofHashedKey(address.getPublicKey(), symbol.toLowerCase());
		txBuilder.down(
			REAddrParticle.class,
			p -> p.getAddr().equals(tokenAddress),
			Optional.of(SubstateWithArg.withArg(new REAddrParticle(tokenAddress), symbol.getBytes(StandardCharsets.UTF_8))),
			"RRI not available"
		);
		txBuilder.up(new TokenDefinitionParticle(
			tokenAddress,
			name,
			getDescription(),
			getIconUrl(),
			getTokenUrl(),
			supply
		));
		txBuilder.up(new TokensParticle(address, supply, tokenAddress));
	}
}
