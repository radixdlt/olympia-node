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

package com.radixdlt.api.store;

import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.api.Rri;
import com.radixdlt.crypto.ECPublicKey;
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

	public JSONObject asJson() {
		return jsonObject()
			.put("name", name)
			.put("rri", Rri.of(symbol, addr))
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

	public String rri() {
		return Rri.of(symbol, addr);
	}

	public UInt384 currentSupply() {
		return currentSupply;
	}

	public TokenDefinitionRecord withSupply(UInt384 supply) {
		if (!mutable) {
			return this;
		}

		return create(symbol, name, addr, description, supply, iconUrl, url, true);
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
