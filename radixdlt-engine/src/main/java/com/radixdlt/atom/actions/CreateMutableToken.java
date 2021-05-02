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

import com.radixdlt.atom.MutableTokenDefinition;
import com.radixdlt.atom.TxAction;
import com.radixdlt.atom.TxBuilder;
import com.radixdlt.atom.TxBuilderException;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atomos.REAddrParticle;
import com.radixdlt.constraintmachine.SubstateWithArg;
import com.radixdlt.identifiers.REAddr;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public final class CreateMutableToken implements TxAction {
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;

	public CreateMutableToken(MutableTokenDefinition def) {
		this(def.getSymbol(), def.getName(), def.getDescription(), def.getIconUrl(), def.getTokenUrl());
	}

	public CreateMutableToken(
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl
	) {
		this.symbol = Objects.requireNonNull(symbol).toLowerCase();
		this.name = Objects.requireNonNull(name);
		this.description = description;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
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
		final var reAddress = txBuilder.getUser().map(a -> REAddr.ofHashedKey(a, getSymbol()))
			.orElse(REAddr.ofNativeToken());

		txBuilder.down(
			REAddrParticle.class,
			p -> p.getAddr().equals(reAddress),
			Optional.of(SubstateWithArg.withArg(new REAddrParticle(reAddress), getSymbol().getBytes(StandardCharsets.UTF_8))),
			"RRI not available"
		);
		txBuilder.up(new TokenDefinitionParticle(
			reAddress,
			getName(),
			getDescription(),
			getIconUrl(),
			getTokenUrl(),
			txBuilder.getUser().orElse(null)
		));
	}
}
