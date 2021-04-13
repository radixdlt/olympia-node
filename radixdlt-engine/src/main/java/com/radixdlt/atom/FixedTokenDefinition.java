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

package com.radixdlt.atom;

import com.radixdlt.utils.UInt256;

import java.util.Objects;

/**
 * Describes a fixed token definition
 */
public final class FixedTokenDefinition {

	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;
	private final UInt256 supply;

	public FixedTokenDefinition(
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl,
		UInt256 supply
	) {
		this.symbol = Objects.requireNonNull(symbol);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
		this.supply = Objects.requireNonNull(supply);
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

	public String getIconUrl() {
		return iconUrl == null ? "" : iconUrl;
	}

	public String getTokenUrl() {
		return tokenUrl == null ? "" : tokenUrl;
	}

	public UInt256 getSupply() {
		return supply;
	}
}
