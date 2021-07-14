/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.application.tokens.state;

import com.radixdlt.atom.REFieldSerialization;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.REAddr;

import java.util.Objects;

public final class TokenResourceMetadata implements Particle {
	private final REAddr addr;
	private final String name;
	private final String description;
	private final String iconUrl;
	private final String url;

	public TokenResourceMetadata(
		REAddr addr,
		String name,
		String description,
		String iconUrl,
		String url
	) {
		this.addr = Objects.requireNonNull(addr);
		this.name = Objects.requireNonNull(name);
		this.description = Objects.requireNonNull(description);
		this.iconUrl = REFieldSerialization.requireValidUrl(iconUrl);
		this.url = REFieldSerialization.requireValidUrl(url);
	}

	public REAddr getAddr() {
		return addr;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getIconUrl() {
		return iconUrl;
	}

	public String getUrl() {
		return url;
	}

	public static TokenResourceMetadata empty(REAddr addr) {
		return new TokenResourceMetadata(addr, "", "", "", "");
	}

	@Override
	public int hashCode() {
		return Objects.hash(addr, name, description, iconUrl, url);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof TokenResourceMetadata)) {
			return false;
		}
		var other = (TokenResourceMetadata) o;
		return Objects.equals(this.addr, other.addr)
			&& Objects.equals(this.name, other.name)
			&& Objects.equals(this.description, other.description)
			&& Objects.equals(this.iconUrl, other.iconUrl)
			&& Objects.equals(this.url, other.url);
	}
}
