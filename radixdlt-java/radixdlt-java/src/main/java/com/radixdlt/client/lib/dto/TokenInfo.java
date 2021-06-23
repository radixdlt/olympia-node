/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public class TokenInfo {
	private final String name;
	private final String rri;
	private final String symbol;
	private final String description;
	private final UInt256 granularity;
	private final boolean isSupplyMutable;
	private final UInt256 currentSupply;
	private final String tokenInfoURL;
	private final String iconURL;

	private TokenInfo(
		String name,
		String rri,
		String symbol,
		String description,
		UInt256 granularity,
		boolean isSupplyMutable,
		UInt256 currentSupply,
		String tokenInfoURL,
		String iconURL
	) {
		this.name = name;
		this.rri = rri;
		this.symbol = symbol;
		this.description = description;
		this.granularity = granularity;
		this.isSupplyMutable = isSupplyMutable;
		this.currentSupply = currentSupply;
		this.tokenInfoURL = tokenInfoURL;
		this.iconURL = iconURL;
	}

	@JsonCreator
	public static TokenInfo create(
		@JsonProperty(value = "name", required = true) String name,
		@JsonProperty(value = "rri", required = true) String rri,
		@JsonProperty(value = "symbol", required = true) String symbol,
		@JsonProperty(value = "description", required = true) String description,
		@JsonProperty(value = "granularity", required = true) UInt256 granularity,
		@JsonProperty(value = "isSupplyMutable", required = true) boolean isSupplyMutable,
		@JsonProperty(value = "currentSupply", required = true) UInt256 currentSupply,
		@JsonProperty(value = "tokenInfoURL", required = true) String tokenInfoURL,
		@JsonProperty(value = "iconURL", required = true) String iconURL
	) {
		requireNonNull(name);
		requireNonNull(rri);
		requireNonNull(symbol);
		requireNonNull(description);
		requireNonNull(granularity);
		requireNonNull(isSupplyMutable);
		requireNonNull(currentSupply);
		requireNonNull(tokenInfoURL);
		requireNonNull(iconURL);

		return new TokenInfo(
			name, rri, symbol, description, granularity,
			isSupplyMutable, currentSupply, tokenInfoURL, iconURL
		);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TokenInfo)) {
			return false;
		}

		var that = (TokenInfo) o;
		return isSupplyMutable == that.isSupplyMutable
			&& name.equals(that.name)
			&& rri.equals(that.rri)
			&& symbol.equals(that.symbol)
			&& description.equals(that.description)
			&& granularity.equals(that.granularity)
			&& currentSupply.equals(that.currentSupply)
			&& tokenInfoURL.equals(that.tokenInfoURL)
			&& iconURL.equals(that.iconURL);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			name, rri, symbol, description, granularity,
			isSupplyMutable, currentSupply, tokenInfoURL, iconURL
		);
	}

	@Override
	public String toString() {
		return "TokenInfo("
			+ "name='" + name + '\''
			+ ", rri=" + rri
			+ ", symbol='" + symbol + '\''
			+ ", description='" + description + '\''
			+ ", granularity=" + granularity
			+ ", isSupplyMutable=" + isSupplyMutable
			+ ", currentSupply=" + currentSupply
			+ ", tokenInfoURL='" + tokenInfoURL + '\''
			+ ", iconURL='" + iconURL + '\''
			+ ')';
	}

	public String getName() {
		return name;
	}

	public String getRri() {
		return rri;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getDescription() {
		return description;
	}

	public UInt256 getGranularity() {
		return granularity;
	}

	public boolean isSupplyMutable() {
		return isSupplyMutable;
	}

	public UInt256 getCurrentSupply() {
		return currentSupply;
	}

	public String getTokenInfoURL() {
		return tokenInfoURL;
	}

	public String getIconURL() {
		return iconURL;
	}
}
