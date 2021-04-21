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

package com.radixdlt.client.store;

import com.radixdlt.atom.actions.CreateFixedToken;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt384;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;

import java.util.Objects;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

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

	@JsonProperty("rri")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Rri rri;

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
		Rri rri,
		String description,
		UInt384 currentSupply,
		String iconUrl,
		String url,
		boolean mutable
	) {
		this.symbol = symbol;
		this.name = name;
		this.rri = rri;
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
		@JsonProperty("rri") Rri rri,
		@JsonProperty("description") String description,
		@JsonProperty("currentSupply") UInt384 currentSupply,
		@JsonProperty("iconUrl") String iconUrl,
		@JsonProperty("url") String url,
		@JsonProperty("mutable") boolean mutable
	) {
		Objects.requireNonNull(symbol);
		Objects.requireNonNull(name);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(description);
		Objects.requireNonNull(currentSupply);

		return new TokenDefinitionRecord(
			symbol, name, rri, description, currentSupply, iconUrl == null ? "" : iconUrl, url == null ? "" : url, mutable
		);
	}

	public static TokenDefinitionRecord create(
		String symbol,
		String name,
		Rri rri,
		String description,
		String iconUrl,
		String url,
		boolean mutable
	) {
		return create(symbol, name, rri, description, UInt384.ZERO, iconUrl, url, mutable);
	}

	public static TokenDefinitionRecord from(RadixAddress user, CreateFixedToken createFixedToken) {
		final Rri rri;
		if (user != null) {
			rri = Rri.of(user.getPublicKey(), createFixedToken.getSymbol());
		} else {
			rri = Rri.ofSystem(createFixedToken.getSymbol());
		}
		return create(
			createFixedToken.getSymbol(),
			createFixedToken.getName(),
			rri,
			createFixedToken.getDescription(),
			UInt384.from(createFixedToken.getSupply()),
			createFixedToken.getIconUrl(),
			createFixedToken.getTokenUrl(),
			false
		);
	}

	public static TokenDefinitionRecord from(RadixAddress user, CreateMutableToken createMutableToken) {
		final Rri rri;
		if (user != null) {
			rri = Rri.of(user.getPublicKey(), createMutableToken.getSymbol());
		} else {
			rri = Rri.ofSystem(createMutableToken.getSymbol());
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
			.put("rri", rri.toString())
			.put("symbol", symbol)
			.put("description", description)
			.put("currentSupply", currentSupply)
			.put("iconUrl", iconUrl)
			.put("url", url)
			.put("mutable", mutable);
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

	public Rri rri() {
		return rri;
	}

	public UInt384 currentSupply() {
		return currentSupply;
	}

	public TokenDefinitionRecord withSupply(UInt384 supply) {
		if (!mutable) {
			return this;
		}

		return create(symbol, name, rri, description, supply, iconUrl, url, true);
	}

	public String toString() {
		return String.format("%s{%s:%s:%s:%s:%s:%s:%s}",
			this.getClass().getSimpleName(), symbol, name, rri, description, currentSupply, iconUrl, url
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
				&& rri.equals(that.rri)
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
		return Objects.hash(symbol, name, rri, description, currentSupply, iconUrl, url, mutable);
	}
}
