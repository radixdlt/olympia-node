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

package com.radixdlt.api.service;

import com.google.inject.Inject;
import com.radixdlt.api.Rri;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

public class TokenService {
	private final ClientApiStore clientApiStore;

	@Inject
	public TokenService(ClientApiStore clientApiStore) {
		this.clientApiStore = clientApiStore;
	}

	public Result<TokenDefinitionRecord> getNativeTokenDescription() {
		REAddr rri = REAddr.ofNativeToken();

		return clientApiStore.getTokenDefinition(rri)
			.flatMap(definition -> withSupply(Rri.of(definition.getSymbol(), rri), definition));
	}

	public Result<TokenDefinitionRecord> getTokenDescription(String rri) {
		return clientApiStore.parseRri(rri)
			.flatMap(clientApiStore::getTokenDefinition)
			.flatMap(definition -> withSupply(rri, definition));
	}

	private Result<TokenDefinitionRecord> withSupply(String rri, TokenDefinitionRecord definition) {
		return definition.isMutable()
			   ? clientApiStore.getTokenSupply(rri).map(definition::withSupply)
			   : Result.ok(definition);
	}
}
