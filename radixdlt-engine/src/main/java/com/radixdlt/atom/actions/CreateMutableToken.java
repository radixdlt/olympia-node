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
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RRI;

import java.util.Objects;
import java.util.Optional;

public final class CreateMutableToken implements TxAction {
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;

	public CreateMutableToken(
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl
	) {
		this.symbol = Objects.requireNonNull(symbol);
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
		final var tokenRRI = txBuilder.getAddress().map(a -> RRI.of(a, symbol))
			.orElse(RRI.from(symbol));

		txBuilder.down(
			RRIParticle.class,
			p -> p.getRri().equals(tokenRRI),
			Optional.of(new RRIParticle(tokenRRI)),
			"RRI not available"
		);
		txBuilder.up(new TokenDefinitionParticle(
			tokenRRI,
			name,
			getDescription(),
			getIconUrl(),
			getTokenUrl(),
			null
		));
	}
}
