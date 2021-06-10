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

import com.radixdlt.crypto.ECPublicKey;

import java.util.Objects;

/**
 * Specifies high level parameters to a token definition
 */
public final class MutableTokenDefinition {
	private final ECPublicKey key;
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;

	public MutableTokenDefinition(
		ECPublicKey key,
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl
	) {
		this.key = key;
		this.symbol = symbol.toLowerCase();
		this.name = Objects.requireNonNull(name);
		this.description = description;
		this.iconUrl = iconUrl;
		this.tokenUrl = tokenUrl;
	}

	public ECPublicKey getKey() {
		return key;
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
}
