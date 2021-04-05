/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.atommodel.tokens;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.utils.UInt256;
import java.util.Objects;

/**
 * Particle representing a mutable supply token definition
 */
@SerializerId2("m_tkn")
public final class MutableSupplyTokenDefinitionParticle extends Particle implements TokenDefinitionParticle {
	@JsonProperty("rri")
	@DsonOutput(Output.ALL)
	private RRI rri;

	@JsonProperty("n")
	@DsonOutput(DsonOutput.Output.ALL)
	private String name;

	@JsonProperty("d")
	@DsonOutput(DsonOutput.Output.ALL)
	private String	description;

	@JsonProperty("g")
	@DsonOutput(Output.ALL)
	private UInt256 granularity;

	@JsonProperty("i")
	@DsonOutput(DsonOutput.Output.ALL)
	private String iconUrl;

	@JsonProperty("url")
	@DsonOutput(Output.ALL)
	private String url;

	MutableSupplyTokenDefinitionParticle() {
		// Serializer only
		super();
	}

	public MutableSupplyTokenDefinitionParticle(
		RRI rri,
		String name,
		String description,
		UInt256 granularity,
		String iconUrl,
		String url
	) {
		this.rri = rri;
		this.name = name;
		this.description = description;
		this.granularity = Objects.requireNonNull(granularity);
		this.iconUrl = iconUrl;
		this.url = url;
	}

	public RRI getRRI() {
		return this.rri;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
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


	@Override
	public String toString() {
		return String.format("%s[(%s:%s:%s), (am%s)]", getClass().getSimpleName(),
			String.valueOf(name), String.valueOf(rri), String.valueOf(granularity),
			String.valueOf(description));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof MutableSupplyTokenDefinitionParticle)) {
			return false;
		}
		MutableSupplyTokenDefinitionParticle that = (MutableSupplyTokenDefinitionParticle) o;
		return Objects.equals(rri, that.rri)
				&& Objects.equals(name, that.name)
				&& Objects.equals(description, that.description)
				&& Objects.equals(granularity, that.granularity)
				&& Objects.equals(iconUrl, that.iconUrl)
				&& Objects.equals(url, that.url);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rri, name, description, granularity, iconUrl, url);
	}
}
