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

import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.junit.Assert;
import org.junit.Test;

import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.atom.actions.CreateMutableToken;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenServiceTest {
	private static final ECPublicKey TOKEN_KEY = ECKeyPair.generateNew().getPublicKey();

	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final TokenService tokenService = new TokenService(clientApiStore, addressing);

	@Test
	public void testGetNativeTokenDescription() {
		var token = REAddr.ofHashedKey(TOKEN_KEY, "xrd");
		var definition = TokenDefinitionRecord.from(TOKEN_KEY, mutableTokenDef("xrd"));

		when(clientApiStore.getTokenDefinition(eq(REAddr.ofNativeToken())))
			.thenReturn(Result.ok(definition));
		when(clientApiStore.getTokenSupply(any()))
			.thenReturn(Result.ok(UInt384.EIGHT));

		tokenService.getNativeTokenDescription()
			.onSuccess(description -> assertEquals(token, description.addr()))
			.onSuccess(description -> assertEquals(UInt384.EIGHT, description.currentSupply()))
			.onFailureDo(Assert::fail);
	}

	@Test
	public void testGetTokenDescription() {
		var token = REAddr.ofHashedKey(TOKEN_KEY, "fff");
		var definition = TokenDefinitionRecord.from(TOKEN_KEY, mutableTokenDef("fff"));

		when(clientApiStore.parseRri(any()))
			.thenReturn(Result.ok(token));
		when(clientApiStore.getTokenDefinition(eq(token)))
			.thenReturn(Result.ok(definition));
		when(clientApiStore.getTokenSupply(any()))
			.thenReturn(Result.ok(UInt384.NINE));

		var rri = addressing.forResources().of("fff", token);
		tokenService.getTokenDescription(rri)
			.onSuccess(description -> assertEquals(token, description.addr()))
			.onSuccess(description -> assertEquals(UInt384.NINE, description.currentSupply()))
			.onFailureDo(Assert::fail);
	}

	private CreateMutableToken mutableTokenDef(String symbol) {
		return new CreateMutableToken(
			TOKEN_KEY,
			symbol,
			symbol,
			description(symbol),
			iconUrl(symbol),
			homeUrl(symbol)
		);
	}

	private String description(String symbol) {
		return "Token with symbol " + symbol;
	}

	private String iconUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/icon";
	}

	private String homeUrl(String symbol) {
		return "https://" + symbol.toLowerCase(Locale.US) + ".coin.com/home";
	}
}