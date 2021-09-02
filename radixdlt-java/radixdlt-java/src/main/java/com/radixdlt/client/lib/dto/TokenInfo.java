/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.client.lib.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.utils.UInt256;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class TokenInfo {
	private final String name;
	private final String rri;
	private final String symbol;
	private final String description;
	private final UInt256 granularity;
	private final boolean isSupplyMutable;
	private final UInt256 currentSupply;
	private final UInt256 totalBurned;
	private final UInt256 totalMinted;
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
		UInt256 totalBurned,
		UInt256 totalMinted,
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
		this.totalBurned = totalBurned;
		this.totalMinted = totalMinted;
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
		@JsonProperty(value = "totalBurned", required = true) UInt256 totalMinted,
		@JsonProperty(value = "totalMinted", required = true) UInt256 totalBurned,
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
		requireNonNull(totalBurned);
		requireNonNull(totalMinted);
		requireNonNull(tokenInfoURL);
		requireNonNull(iconURL);

		return new TokenInfo(
			name, rri, symbol, description, granularity,
			isSupplyMutable, currentSupply, totalBurned, totalMinted,
			tokenInfoURL, iconURL
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
			&& totalBurned.equals(that.totalBurned)
			&& totalMinted.equals(that.totalMinted)
			&& tokenInfoURL.equals(that.tokenInfoURL)
			&& iconURL.equals(that.iconURL);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			name, rri, symbol, description, granularity,
			isSupplyMutable, currentSupply, totalBurned, totalMinted,
			tokenInfoURL, iconURL
		);
	}

	@Override
	public String toString() {
		return "TokenInfo{"
			+ "name='" + name + '\''
			+ ", rri='" + rri + '\''
			+ ", symbol='" + symbol + '\''
			+ ", description='" + description + '\''
			+ ", granularity=" + granularity
			+ ", isSupplyMutable=" + isSupplyMutable
			+ ", currentSupply=" + currentSupply
			+ ", totalBurned=" + totalBurned
			+ ", totalMinted=" + totalMinted
			+ ", tokenInfoURL='" + tokenInfoURL + '\''
			+ ", iconURL='" + iconURL + '\'' + '}';
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

	public UInt256 getTotalBurned() {
		return totalBurned;
	}

	public UInt256 getTotalMinted() {
		return totalMinted;
	}

	public String getTokenInfoURL() {
		return tokenInfoURL;
	}

	public String getIconURL() {
		return iconURL;
	}
}
