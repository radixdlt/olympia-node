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

import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.Rri;
import com.radixdlt.utils.UInt256;

import java.util.Objects;
import java.util.Optional;

/**
 * Particle representing a fixed supply token definition
 */
public final class TokenDefinitionParticle implements Particle {
	private final Rri rri;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String url;
	private final ECPublicKey minter;
	private final UInt256 supply;

	public TokenDefinitionParticle(
		Rri rri,
		String name,
		String description,
		String iconUrl,
		String url,
		UInt256 supply,
		ECPublicKey minter
	) {
		this.rri = Objects.requireNonNull(rri);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.iconUrl = Objects.requireNonNull(iconUrl);
		this.url = Objects.requireNonNull(url);

		if (supply != null && minter != null) {
			throw new IllegalArgumentException("Can't have fixed supply and minter");
		}

		this.supply = supply;
		this.minter = minter;
	}

	public TokenDefinitionParticle(
		Rri rri,
		String name,
		String description,
		String iconUrl,
		String url,
		UInt256 supply
	) {
		this(rri, name, description, iconUrl, url, Objects.requireNonNull(supply), null);
	}

	public TokenDefinitionParticle(
		Rri rri,
		String name,
		String description,
		String iconUrl,
		String url,
		ECPublicKey minter
	) {
		this(rri, name, description, iconUrl, url, null, minter);
	}

	public Optional<ECPublicKey> getMinter() {
		return Optional.ofNullable(minter);
	}

	public boolean isMutable() {
		return this.supply == null;
	}

	public Rri getRri() {
		return rri;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public Optional<UInt256> getSupply() {
		return Optional.ofNullable(this.supply);
	}

	public String getIconUrl() {
		return this.iconUrl;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public String toString() {
		return String.format("%s[(%s:%s:%s), (%s)]", getClass().getSimpleName(),
			this.rri, name, supply, description);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TokenDefinitionParticle)) {
			return false;
		}
		TokenDefinitionParticle that = (TokenDefinitionParticle) o;
		return Objects.equals(rri, that.rri)
			&& Objects.equals(name, that.name)
			&& Objects.equals(description, that.description)
			&& Objects.equals(supply, that.supply)
			&& Objects.equals(iconUrl, that.iconUrl)
			&& Objects.equals(url, that.url)
			&& Objects.equals(minter, that.minter);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rri, name, description, supply, iconUrl, url, minter);
	}
}
