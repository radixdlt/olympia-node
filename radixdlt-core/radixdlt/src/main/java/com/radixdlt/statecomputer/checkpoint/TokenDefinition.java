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

package com.radixdlt.statecomputer.checkpoint;

import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.utils.UInt256;

import java.util.Map;
import java.util.Objects;

/**
 * Specifies high level parameters to a token definition
 */
public final class TokenDefinition {
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;
	private final UInt256 granularity = UInt256.ONE;
	private final Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions;

	public TokenDefinition(
        String symbol,
        String name,
        String description,
        String iconUrl,
        String tokenUrl,
        Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions
	) {
		this.symbol = Objects.requireNonNull(symbol);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.iconUrl = Objects.requireNonNull(iconUrl);
		this.tokenUrl = Objects.requireNonNull(tokenUrl);
		this.tokenPermissions = Objects.requireNonNull(tokenPermissions);
	}

	public String getSymbol() {
		return symbol;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public UInt256 getGranularity() {
		return granularity;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public String getTokenUrl() {
		return tokenUrl;
	}

	public Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}
}
