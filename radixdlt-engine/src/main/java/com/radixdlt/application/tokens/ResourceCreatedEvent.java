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

package com.radixdlt.application.tokens;

import com.radixdlt.application.tokens.state.TokenResource;
import com.radixdlt.application.tokens.state.TokenResourceMetadata;
import com.radixdlt.constraintmachine.REEvent;

public final class ResourceCreatedEvent implements REEvent {
	private final String symbol;
	private final TokenResource tokenResource;
	private final TokenResourceMetadata metadata;

	public ResourceCreatedEvent(String symbol, TokenResource tokenResource, TokenResourceMetadata metadata) {
		this.symbol = symbol;
		this.tokenResource = tokenResource;
		this.metadata = metadata;
	}

	public String getSymbol() {
		return symbol;
	}

	public TokenResource getTokenResource() {
		return tokenResource;
	}

	public TokenResourceMetadata getMetadata() {
		return metadata;
	}
}
