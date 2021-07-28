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

package com.radixdlt.api.store;

import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.networks.Addressing;
import com.radixdlt.utils.UInt384;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

@SerializerId2("radix.api.token")
public class TokenDefinitionRecord {
	@JsonProperty(SerializerConstants.SERIALIZER_NAME)
	@DsonOutput(DsonOutput.Output.ALL)
	private SerializerDummy serializer = SerializerDummy.DUMMY;

	@JsonProperty("symbol")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String symbol;

	@JsonProperty("name")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String name;

	@JsonProperty("addr")
	@DsonOutput(DsonOutput.Output.ALL)
	private final REAddr addr;

	@JsonProperty("description")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String description;

	@JsonProperty("currentSupply")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt384 currentSupply;

	@JsonProperty("iconUrl")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String iconUrl;

	@JsonProperty("url")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String url;

	@JsonProperty("mutable")
	@DsonOutput(DsonOutput.Output.ALL)
	private final boolean mutable;

	private TokenDefinitionRecord(
		String symbol,
		String name,
		REAddr addr,
		String description,
		UInt384 currentSupply,
		String iconUrl,
		String url,
		boolean mutable
	) {
		this.symbol = symbol;
		this.name = name;
		this.addr = addr;
		this.description = description;
		this.currentSupply = currentSupply;
		this.iconUrl = iconUrl;
		this.url = url;
		this.mutable = mutable;
	}

	@JsonCreator
	public static TokenDefinitionRecord create(
		@JsonProperty("symbol") String symbol,
		@JsonProperty("name") String name,
		@JsonProperty("addr") REAddr addr,
		@JsonProperty("description") String description,
		@JsonProperty("currentSupply") UInt384 currentSupply,
		@JsonProperty("iconUrl") String iconUrl,
		@JsonProperty("url") String url,
		@JsonProperty("mutable") boolean mutable
	) {
		Objects.requireNonNull(symbol);
		Objects.requireNonNull(name);
		Objects.requireNonNull(addr);
		Objects.requireNonNull(currentSupply);

		return new TokenDefinitionRecord(
			symbol,
			name,
			addr,
			description == null ? "" : description,
			currentSupply,
			iconUrl == null ? "" : iconUrl,
			url == null ? "" : url,
			mutable
		);
	}

	public static TokenDefinitionRecord create(
		String symbol,
		String name,
		REAddr rri,
		String description,
		String iconUrl,
		String url,
		boolean mutable
	) {
		return create(symbol, name, rri, description, UInt384.ZERO, iconUrl, url, mutable);
	}

	public static TokenDefinitionRecord from(CreateFixedToken createFixedToken) {
		final REAddr resourceAddr = createFixedToken.getResourceAddr();
		return create(
			createFixedToken.getSymbol(),
			createFixedToken.getName(),
			resourceAddr,
			createFixedToken.getDescription(),
			UInt384.from(createFixedToken.getSupply()),
			createFixedToken.getIconUrl(),
			createFixedToken.getTokenUrl(),
			false
		);
	}

	public static TokenDefinitionRecord from(ECPublicKey user, CreateMutableToken createMutableToken) {
		final REAddr rri;
		if (user != null) {
			rri = REAddr.ofHashedKey(user, createMutableToken.getSymbol());
		} else {
			rri = REAddr.ofNativeToken();
		}
		return create(
			createMutableToken.getSymbol(),
			createMutableToken.getName(),
			rri,
			createMutableToken.getDescription(),
			UInt384.ZERO,
			createMutableToken.getIconUrl(),
			createMutableToken.getTokenUrl(),
			true
		);
	}

	public JSONObject asJson(Addressing addressing) {
		return jsonObject()
			.put("name", name)
			.put("rri", addressing.forResources().of(symbol, addr))
			.put("symbol", symbol)
			.put("description", description)
			.put("currentSupply", currentSupply)
			.put("iconURL", iconUrl)
			.put("tokenInfoURL", url)
			.put("granularity", "1") // hardcoded for now
			.put("isSupplyMutable", mutable);
	}

	public String getSymbol() {
		return symbol;
	}

	public String getName() {
		return name;
	}

	public boolean isMutable() {
		return mutable;
	}

	public REAddr addr() {
		return addr;
	}

	public String rri(Addressing addressing) {
		return addressing.forResources().of(symbol, addr);
	}

	public UInt384 currentSupply() {
		return currentSupply;
	}

	public TokenDefinitionRecord withSupply(UInt384 supply) {
		return create(symbol, name, addr, description, supply, iconUrl, url, mutable);
	}

	public String toString() {
		return String.format("%s{%s:%s:%s:%s:%s:%s:%s}",
			this.getClass().getSimpleName(), symbol, name, addr, description, currentSupply, iconUrl, url
		);
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) {
			return true;
		}

		if (o instanceof TokenDefinitionRecord) {
			var that = (TokenDefinitionRecord) o;

			return mutable == that.mutable
				&& name.equals(that.name)
				&& addr.equals(that.addr)
				&& Objects.equals(symbol, that.symbol)
				&& Objects.equals(currentSupply, that.currentSupply)
				&& Objects.equals(description, that.description)
				&& Objects.equals(iconUrl, that.iconUrl)
				&& Objects.equals(url, that.url);
		}

		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(symbol, name, addr, description, currentSupply, iconUrl, url, mutable);
	}
}
