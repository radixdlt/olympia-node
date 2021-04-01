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
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.SerializerConstants;
import com.radixdlt.serialization.SerializerDummy;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.Pair;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

	@JsonProperty("permissions")
	@DsonOutput(DsonOutput.Output.ALL)
	private final Map<String, TokenPermission> tokenPermissions;

	private TokenDefinitionRecord(
		String name,
		RRI rri,
		String description,
		UInt256 granularity,
		UInt256 currentSupply,
		String iconUrl,
		String url,
		boolean mutable,
		Map<String, TokenPermission> tokenPermissions
	) {
		this.name = name;
		this.rri = rri;
		this.description = description;
		this.granularity = granularity;
		this.currentSupply = currentSupply;
		this.iconUrl = iconUrl;
		this.url = url;
		this.mutable = mutable;
		this.tokenPermissions = tokenPermissions;
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
		@JsonProperty("mutable") boolean mutable,
		@JsonProperty("permissions") Map<String, TokenPermission> tokenPermissions
	) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(rri);
		Objects.requireNonNull(description);
		Objects.requireNonNull(granularity);
		Objects.requireNonNull(currentSupply);

		return new TokenDefinitionRecord(
			name, rri, description, granularity, currentSupply, iconUrl, url, mutable,
			Optional.ofNullable(tokenPermissions).orElse(Map.of())
		);
	}

	public static TokenDefinitionRecord create(
		String name,
		RRI rri,
		String description,
		UInt256 granularity,
		String iconUrl,
		String url,
		boolean mutable,
		Map<String, TokenPermission> tokenPermissions
	) {
		return create(name, rri, description, granularity, UInt256.ZERO, iconUrl, url, mutable, tokenPermissions);
	}

	public static Result<TokenDefinitionRecord> from(Particle particle) {
		if (particle instanceof MutableSupplyTokenDefinitionParticle) {
			return Result.ok(from((MutableSupplyTokenDefinitionParticle) particle, UInt256.ZERO));
		} else if (particle instanceof FixedSupplyTokenDefinitionParticle) {
			return Result.ok(from((FixedSupplyTokenDefinitionParticle) particle));
		}

		return Result.fail("Unknown token definition particle: {}", particle);
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
			true,
			convertPermissions(definition.getTokenPermissions())
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
			false,
			Map.of()
		);
	}

	public JSONObject asJson() {
		return jsonObject()
			.put("name", name)
			.put("rri", rri)
			.put("description", description)
			.put("granularity", granularity)
			.put("currentSupply", currentSupply)
			.put("iconUrl", iconUrl)
			.put("url", url)
			.put("mutable", mutable)
			.put("tokenPermissions", tokenPermissions);
	}

	public String toKey() {
		return rri.toString();
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

		return create(name, rri, description, granularity, supply, iconUrl, url, true, tokenPermissions);
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
				&& description.equals(that.description)
				&& granularity.equals(that.granularity)
				&& currentSupply.equals(that.currentSupply)
				&& iconUrl.equals(that.iconUrl)
				&& url.equals(that.url)
				&& tokenPermissions.equals(that.tokenPermissions);
		}

		return false;
	}

	@Override
	public final int hashCode() {
		return Objects.hash(name, rri, description, granularity, currentSupply, iconUrl, url, mutable, tokenPermissions);
	}

	private static Map<String, TokenPermission> convertPermissions(
		Map<MutableSupplyTokenDefinitionParticle.TokenTransition, TokenPermission> tokenPermissions
	) {
		return tokenPermissions
			.entrySet()
			.stream()
			.map(e -> Pair.of(e.getKey().name(), e.getValue()))
			.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
	}
}
