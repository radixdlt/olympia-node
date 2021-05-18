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
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

public class CreateFixedToken implements TxAction {
	private final REAddr resourceAddr;
	private final REAddr accountAddr;
	private final String symbol;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String tokenUrl;
	private final UInt256 supply;

	public CreateFixedToken(
		REAddr resourceAddr,
		REAddr accountAddr,
		String symbol,
		String name,
		String description,
		String iconUrl,
		String tokenUrl,
		UInt256 supply
	) {
		this.resourceAddr = Objects.requireNonNull(resourceAddr);
		this.accountAddr = Objects.requireNonNull(accountAddr);
		this.symbol = Objects.requireNonNull(symbol);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.iconUrl = Objects.requireNonNull(iconUrl);
		this.tokenUrl = Objects.requireNonNull(tokenUrl);
		this.supply = Objects.requireNonNull(supply);
	}

	public REAddr getResourceAddr() {
		return resourceAddr;
	}

	public REAddr getAccountAddr() {
		return accountAddr;
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
		return description;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public String getTokenUrl() {
		return tokenUrl;
	}
}
