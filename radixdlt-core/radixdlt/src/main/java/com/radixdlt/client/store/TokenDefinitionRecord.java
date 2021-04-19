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

import com.radixdlt.utils.UInt384;
import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atommodel.tokens.TokenDefinitionParticle;
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
		String name,
		Rri rri,
		String description,
		UInt384 currentSupply,
		String iconUrl,
		String url,
		boolean mutable
	) {
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
		@JsonProperty("name") String name,
		@JsonProperty("rri") Rri rri,
		@JsonProperty("description") String description,
		@JsonProperty("currentSupply") UInt384 currentSupply,
		@JsonProperty("iconUrl") String iconUrl,
		@JsonProperty("url") String url,
		@JsonProperty("mutable") boolean mutable
	) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(description);
		Objects.requireNonNull(currentSupply);

		return new TokenDefinitionRecord(
			name, rri, description, currentSupply, iconUrl == null ? "" : iconUrl, url == null ? "" : url, mutable
		);
	}

	public static TokenDefinitionRecord create(
		String name,
		Rri rri,
		String description,
		String iconUrl,
		String url,
		boolean mutable
	) {
		return create(name, rri, description, UInt384.ZERO, iconUrl, url, mutable);
	}

	public static TokenDefinitionRecord from(TokenDefinitionParticle definition) {
		return create(
			definition.getName(),
			definition.getRri(),
			definition.getDescription(),
			definition.getSupply().map(UInt384::from).orElse(UInt384.ZERO),
			definition.getIconUrl(),
			definition.getUrl(),
			definition.isMutable()
		);
	}

	public static TokenDefinitionRecord from(TokenDefinitionParticle definition, UInt384 supply) {
		return create(
			definition.getName(),
			definition.getRri(),
			definition.getDescription(),
			supply,
			definition.getIconUrl(),
			definition.getUrl(),
			definition.isMutable()
		);
	}

	public JSONObject asJson(byte magic) {
		return jsonObject()
			.put("name", name)
			.put("rri", rri.toSpecString(magic))
			.put("symbol", rri.getName())
			.put("description", description)
			.put("currentSupply", currentSupply)
			.put("iconUrl", iconUrl)
			.put("url", url)
			.put("mutable", mutable);
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

		return create(name, rri, description, supply, iconUrl, url, true);
	}

	public String toString() {
		return String.format("%s{%s:%s:%s:%s:%s:%s}",
			this.getClass().getSimpleName(), name, rri, description, currentSupply, iconUrl, url
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
				&& Objects.equals(currentSupply, that.currentSupply)
				&& Objects.equals(description, that.description)
				&& Objects.equals(iconUrl, that.iconUrl)
				&& Objects.equals(url, that.url);
		}

		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(name, rri, description, currentSupply, iconUrl, url, mutable);
	}
}
