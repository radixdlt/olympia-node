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

import org.json.JSONObject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenDefinitionSubstate;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

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
	private final RRI rri;

	@JsonProperty("description")
	@DsonOutput(DsonOutput.Output.ALL)
	private final String description;

	@JsonProperty("granularity")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 granularity;

	@JsonProperty("currentSupply")
	@DsonOutput(DsonOutput.Output.ALL)
	private final UInt256 currentSupply;

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
		RRI rri,
		String description,
		UInt256 granularity,
		UInt256 currentSupply,
		String iconUrl,
		String url,
		boolean mutable
	) {
		this.name = name;
		this.rri = rri;
		this.description = description;
		this.granularity = granularity;
		this.currentSupply = currentSupply;
		this.iconUrl = iconUrl;
		this.url = url;
		this.mutable = mutable;
	}

	@JsonCreator
	public static TokenDefinitionRecord create(
		@JsonProperty("name") String name,
		@JsonProperty("rri") RRI rri,
		@JsonProperty("description") String description,
		@JsonProperty("granularity") UInt256 granularity,
		@JsonProperty("currentSupply") UInt256 currentSupply,
		@JsonProperty("iconUrl") String iconUrl,
		@JsonProperty("url") String url,
		@JsonProperty("mutable") boolean mutable
	) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(granularity);
		Objects.requireNonNull(currentSupply);

		return new TokenDefinitionRecord(
			name, rri, description, granularity, currentSupply, iconUrl, url, mutable
		);
	}

	public static TokenDefinitionRecord create(
		String name,
		RRI rri,
		String description,
		UInt256 granularity,
		String iconUrl,
		String url,
		boolean mutable
	) {
		return create(name, rri, description, granularity, UInt256.ZERO, iconUrl, url, mutable);
	}

	public static Result<TokenDefinitionRecord> from(TokenDefinitionSubstate substate) {
		if (substate instanceof MutableSupplyTokenDefinitionParticle) {
			return Result.ok(from((MutableSupplyTokenDefinitionParticle) substate, UInt256.ZERO));
		} else if (substate instanceof FixedSupplyTokenDefinitionParticle) {
			return Result.ok(from((FixedSupplyTokenDefinitionParticle) substate));
		}

		return Result.fail("Unknown token definition substate: {0}", substate);
	}

	public static TokenDefinitionRecord from(MutableSupplyTokenDefinitionParticle definition, UInt256 supply) {
		return create(
			definition.getName(),
			definition.getRRI(),
			definition.getDescription(),
			definition.getGranularity(),
			supply,
			definition.getIconUrl(),
			definition.getUrl(),
			true
		);
	}

	public static TokenDefinitionRecord from(FixedSupplyTokenDefinitionParticle definition) {
		return create(
			definition.getName(),
			definition.getRRI(),
			definition.getDescription(),
			definition.getGranularity(),
			definition.getSupply(),
			definition.getIconUrl(),
			definition.getUrl(),
			false
		);
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("name", name)
			.put("rri", rri)
			.put("symbol", rri.getName())
			.put("description", description)
			.put("granularity", granularity)
			.put("currentSupply", currentSupply)
			.put("iconUrl", iconUrl)
			.put("url", url)
			.put("mutable", mutable);
	}

	public boolean isMutable() {
		return mutable;
	}

	public RRI rri() {
		return rri;
	}

	public UInt256 currentSupply() {
		return currentSupply;
	}

	public TokenDefinitionRecord withSupply(UInt256 supply) {
		if (!mutable) {
			return this;
		}

		return create(name, rri, description, granularity, supply, iconUrl, url, true);
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
				&& granularity.equals(that.granularity)
				&& currentSupply.equals(that.currentSupply)
				&& Objects.equals(description, that.description)
				&& Objects.equals(iconUrl, that.iconUrl)
				&& Objects.equals(url, that.url);
		}

		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(name, rri, description, granularity, currentSupply, iconUrl, url, mutable);
	}
}
