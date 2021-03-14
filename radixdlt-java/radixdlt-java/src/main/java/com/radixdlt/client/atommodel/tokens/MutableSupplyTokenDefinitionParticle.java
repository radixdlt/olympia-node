/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.constraintmachine.Particle;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RRI;

@SerializerId2("radix.particles.mutable_supply_token_definition")
public class MutableSupplyTokenDefinitionParticle extends Particle {
	public enum TokenTransition {
		MINT,
		BURN
	}

	@JsonProperty("rri")
	@DsonOutput(Output.ALL)
	private RRI rri;

	@JsonProperty("name")
	@DsonOutput(Output.ALL)
	private String name;

	@JsonProperty("description")
	@DsonOutput(Output.ALL)
	private String description;

	@JsonProperty("granularity")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("iconUrl")
	@DsonOutput(Output.ALL)
	private String iconUrl;

	@JsonProperty("url")
	@DsonOutput(Output.ALL)
	private String url;

	private Map<TokenTransition, TokenPermission> tokenPermissions;

	MutableSupplyTokenDefinitionParticle() {
		// Empty constructor for serializer
	}

	public MutableSupplyTokenDefinitionParticle(
		RadixAddress address,
		String name,
		String symbol,
		String description,
		UInt256 granularity,
		Map<TokenTransition, TokenPermission> tokenPermissions,
		String iconUrl,
		String url
	) {
		this(RRI.of(address, symbol), name, description, granularity, tokenPermissions, iconUrl, url);
	}

	public MutableSupplyTokenDefinitionParticle(
		RRI rri,
		String name,
		String description,
		UInt256 granularity,
		Map<TokenTransition, TokenPermission> tokenPermissions,
		String iconUrl,
		String url
	) {
		this.rri = rri;
		this.name = name;
		this.description = description;
		this.granularity = granularity;
		this.tokenPermissions = ImmutableMap.copyOf(tokenPermissions);
		this.iconUrl = iconUrl;
		this.url = url;
	}

	@Override
	public Set<EUID> getDestinations() {
		return ImmutableSet.of(this.rri.getAddress().euid());
	}

	public RRI getRRI() {
		return this.rri;
	}

	public Map<TokenTransition, TokenPermission> getTokenPermissions() {
		return tokenPermissions;
	}

	public RadixAddress getAddress() {
		return this.rri.getAddress();
	}

	public String getName() {
		return name;
	}

	public String getSymbol() {
		return this.rri.getName();
	}

	public String getDescription() {
		return description;
	}

	public UInt256 getGranularity() {
		return this.granularity;
	}

	public String getIconUrl() {
		return this.iconUrl;
	}

	public String getUrl() {
		return url;
	}

	@JsonProperty("permissions")
	@DsonOutput(value = {Output.ALL})
	private Map<String, String> getJsonPermissions() {
		return this.tokenPermissions.entrySet().stream()
			.collect(Collectors.toMap(e -> e.getKey().name().toLowerCase(), e -> e.getValue().name().toLowerCase()));
	}

	@JsonProperty("permissions")
	private void setJsonPermissions(Map<String, String> permissions) {
		if (permissions != null) {
			this.tokenPermissions = permissions.entrySet().stream()
				.collect(Collectors.toMap(
					e -> TokenTransition.valueOf(e.getKey().toUpperCase()),
					e -> TokenPermission.valueOf(e.getValue().toUpperCase())
				));
		} else {
			throw new IllegalArgumentException("Permissions cannot be null.");
		}
	}

	@Override
	public String toString() {
		String tokenPermissionsStr = (tokenPermissions == null)
			? "null"
			: tokenPermissions.entrySet().stream().map(e -> String.format("%s:%s", e.getKey().toString().toLowerCase(),
				e.getValue().toString().toLowerCase())).collect(Collectors.joining(","));
		return String.format("%s[%s (%s:%s), (%s:%s)]", getClass().getSimpleName(),
			String.valueOf(this.rri), name, description,
			String.valueOf(granularity), tokenPermissionsStr);
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MutableSupplyTokenDefinitionParticle)) {
			return false;
		}
		var that = (MutableSupplyTokenDefinitionParticle) o;
		return Objects.equals(rri, that.rri)
			&& Objects.equals(name, that.name)
			&& Objects.equals(description, that.description)
			&& Objects.equals(granularity, that.granularity)
			&& Objects.equals(iconUrl, that.iconUrl)
			&& Objects.equals(url, that.url)
			&& Objects.equals(tokenPermissions, that.tokenPermissions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rri, name, description, granularity, iconUrl, url, tokenPermissions);
	}
}
