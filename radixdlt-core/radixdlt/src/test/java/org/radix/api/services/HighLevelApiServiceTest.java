package org.radix.api.services;/*
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

import com.radixdlt.atom.TxLowLevelBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.atom.Atom;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.atommodel.tokens.TokenPermission;
import com.radixdlt.client.store.ClientApiStore;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HighLevelApiServiceTest {
	private static final RadixAddress OWNER = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
	private static final RadixAddress TOKEN_ADDRESS = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
	private static final RRI TOKEN = RRI.of(TOKEN_ADDRESS, "XRD");

	private final Universe universe = mock(Universe.class);
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private Atom genesisAtom;
	private HighLevelApiService highLevelApiService;

	@Before
	public void setup() {
		var permissions = Map.of(
			TokenTransition.MINT, TokenPermission.ALL,
			TokenTransition.BURN, TokenPermission.ALL
		);

		var nativeTokenParticle = new MutableSupplyTokenDefinitionParticle(
			TOKEN, "XRD", "XRD XRD", UInt256.ONE, "", "", permissions
		);

		genesisAtom = TxLowLevelBuilder.newBuilder()
			.up(nativeTokenParticle)
			.buildWithoutSignature();

		highLevelApiService = new HighLevelApiService(universe, clientApiStore, genesisAtom);
	}

	@Test
	public void testGetUniverseMagic() {
		when(universe.getMagic()).thenReturn(1234);

		assertEquals(1234, highLevelApiService.getUniverseMagic());
	}

	@Test
	public void testGetTokenBalances() {
		var balance1 = TokenBalance.create(RRI.of(TOKEN_ADDRESS, "FOO"), UInt256.FIVE);
		var balance2 = TokenBalance.create(RRI.of(TOKEN_ADDRESS, "BAR"), UInt256.NINE);
		var balances = Result.ok(List.of(balance1, balance2));

		when(clientApiStore.getTokenBalances(OWNER))
			.thenReturn(balances);

		highLevelApiService.getTokenBalances(OWNER)
			.onSuccess(list -> {
				assertEquals(2, list.size());
				assertEquals(UInt256.FIVE, list.get(0).getAmount());
				assertEquals(UInt256.NINE, list.get(1).getAmount());
			})
			.onFailureDo(Assert::fail);
	}

	@Test
	public void testGetNativeTokenDescription() {
		when(clientApiStore.getTokenSupply(TOKEN))
			.thenReturn(Result.ok(UInt256.SEVEN));

		highLevelApiService.getNativeTokenDescription()
			.onSuccess(token -> assertTrue(token.isMutable()))
			.onSuccess(token -> assertEquals(TOKEN, token.rri()))
			.onSuccess(token -> assertEquals(UInt256.SEVEN, token.currentSupply()))
			.onFailureDo(Assert::fail);
	}

	@Test
	public void testGetTokenDescription() {
		var token = RRI.of(TOKEN_ADDRESS, "FOO");
		var definition = TokenDefinitionRecord.from(mutableTokenDef("FOO"));

		when(clientApiStore.getTokenDefinition(token))
			.thenReturn(definition);
		when(clientApiStore.getTokenSupply(token))
			.thenReturn(Result.ok(UInt256.NINE));

		highLevelApiService.getTokenDescription(token)
			.onSuccess(description -> assertEquals(token, description.rri()))
			.onSuccess(description -> assertEquals(UInt256.NINE, description.currentSupply()))
			.onFailureDo(Assert::fail);
	}


	private MutableSupplyTokenDefinitionParticle mutableTokenDef(String symbol) {
		return new MutableSupplyTokenDefinitionParticle(
			RRI.of(TOKEN_ADDRESS, symbol),
			symbol,
			description(symbol),
			UInt256.ONE,
			iconUrl(symbol),
			homeUrl(symbol),
			Map.of(
				TokenTransition.MINT, TokenPermission.ALL,
				TokenTransition.BURN, TokenPermission.ALL
			)
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