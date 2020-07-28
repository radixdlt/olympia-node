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

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.Objects;

import com.radixdlt.client.application.translate.Action;

public class CreateTokenAction implements Action {
	public enum TokenSupplyType {
		FIXED,
		MUTABLE
	}

	private final String name;
	private final RRI rri;
	private final String description;
	private final String iconUrl;
	private final String url;
	private final BigDecimal initialSupply;
	private final BigDecimal granularity;
	private final TokenSupplyType tokenSupplyType;

	private CreateTokenAction(
		RRI rri,
		String name,
		String description,
		String iconUrl,
		String url,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		// Redundant null check added for completeness
		Objects.requireNonNull(initialSupply);

		if (initialSupply.compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Supply cannot be less than 0.");
		}

		if (tokenSupplyType.equals(TokenSupplyType.FIXED) && initialSupply.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("Fixed supply must be greater than 0.");
		}

		this.name = Objects.requireNonNull(name);
		this.rri = Objects.requireNonNull(rri);
		this.description = description;
		this.iconUrl = iconUrl;
		this.url = url;
		this.initialSupply = initialSupply;
		this.granularity = Objects.requireNonNull(granularity);
		this.tokenSupplyType = Objects.requireNonNull(tokenSupplyType);
	}

	public static CreateTokenAction create(
		RRI tokenRRI,
		String name,
		String description,
		String iconUrl,
		String url,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return new CreateTokenAction(tokenRRI, name, description, iconUrl, url, initialSupply, granularity, tokenSupplyType);
	}

	public static CreateTokenAction create(
		RRI tokenRRI,
		String name,
		String description,
		BigDecimal initialSupply,
		BigDecimal granularity,
		TokenSupplyType tokenSupplyType
	) {
		return create(tokenRRI, name, description, null, null, initialSupply, granularity, tokenSupplyType);
	}

	public RRI getRRI() {
		return rri;
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

	public BigDecimal getInitialSupply() {
		return initialSupply;
	}

	public BigDecimal getGranularity() {
		return granularity;
	}

	public TokenSupplyType getTokenSupplyType() {
		return tokenSupplyType;
	}

	@Override
	public String toString() {
		return "CREATE TOKEN " + rri + " " + tokenSupplyType;
	}
}
