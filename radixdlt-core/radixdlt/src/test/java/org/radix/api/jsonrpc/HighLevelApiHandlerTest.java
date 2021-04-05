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
package org.radix.api.jsonrpc;

import org.junit.Test;
import org.radix.api.jsonrpc.handler.HighLevelApiHandler;
import org.radix.api.services.HighLevelApiService;

import com.radixdlt.client.store.ActionEntry;
import com.radixdlt.client.store.MessageEntry;
import com.radixdlt.client.store.TokenBalance;
import com.radixdlt.client.store.TokenDefinitionRecord;
import com.radixdlt.client.store.TxHistoryEntry;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;
import com.radixdlt.utils.functional.Tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static com.radixdlt.utils.functional.Tuple.tuple;

public class HighLevelApiHandlerTest {
	private static final String KNOWN_ADDRESS_STRING = "JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor";
	private static final RadixAddress KNOWN_ADDRESS = RadixAddress.from(KNOWN_ADDRESS_STRING);

	@Test
	public void testTokenBalance() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		var balance1 = TokenBalance.create(RRI.of(KNOWN_ADDRESS, "XYZ"), UInt256.TWO);
		var balance2 = TokenBalance.create(RRI.of(KNOWN_ADDRESS, "YZX"), UInt256.FIVE);
		var balance3 = TokenBalance.create(RRI.of(KNOWN_ADDRESS, "ZXY"), UInt256.EIGHT);

		when(service.getTokenBalances(any(RadixAddress.class)))
			.thenReturn(Result.ok(List.of(balance1, balance2, balance3)));

		var params = jsonObject().put("address", KNOWN_ADDRESS_STRING);
		var response = handler.handleTokenBalances(jsonObject().put("id", "1").put("params", params));

		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertEquals(KNOWN_ADDRESS_STRING, result.getString("owner"));

		var list = result.getJSONArray("tokenBalances");
		assertEquals(3, list.length());
		assertEquals("2", list.getJSONObject(0).getString("amount"));
		assertEquals("5", list.getJSONObject(1).getString("amount"));
		assertEquals("8", list.getJSONObject(2).getString("amount"));
	}

	@Test
	public void testNativeToken() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		when(service.getNativeTokenDescription())
			.thenReturn(buildNativeToken());

		var response = handler.handleNativeToken(jsonObject().put("id", "1"));
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertNotNull(result);
		assertEquals("XRD", result.getString("name"));
		assertEquals("XRD XRD", result.getString("description"));
		assertEquals(UInt256.ONE, result.get("granularity"));
		assertEquals(UInt256.EIGHT, result.get("currentSupply"));
	}

	@Test
	public void testTokenInfo() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		when(service.getTokenDescription(any(RRI.class)))
			.thenReturn(buildToken("FOO"));

		var params = jsonObject().put("resourceIdentifier", RRI.of(KNOWN_ADDRESS, "FOO").toString());
		var response = handler.handleTokenInfo(jsonObject().put("id", "1").put("params", params));
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertNotNull(result);
		assertEquals("FOO", result.getString("name"));
		assertEquals("FOO FOO", result.getString("description"));
		assertEquals(UInt256.ONE, result.get("granularity"));
		assertEquals(UInt256.EIGHT, result.get("currentSupply"));
	}

	@Test
	public void testTransactionHistory() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		var now = Instant.now();
		var action = ActionEntry.unknown();
		var entry = TxHistoryEntry.create(
			AID.ZERO, now, UInt256.ONE, MessageEntry.create("text", "scheme"), List.of(action)
		);

		when(service.getTransactionHistory(any(), eq(5), any()))
			.thenReturn(Result.ok(tuple(Optional.ofNullable(now), List.of(entry))));

		var params = jsonObject().put("address", KNOWN_ADDRESS_STRING).put("size", 5);
		var response = handler.handleTransactionHistory(jsonObject().put("id", "1").put("params", params));

		assertNotNull(response);

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));
		assertTrue(result.has("transactions"));
		var transactions = result.getJSONArray("transactions");
		assertEquals(1, transactions.length());

		var singleTransaction = transactions.getJSONObject(0);
		assertEquals(UInt256.ONE, singleTransaction.get("fee"));

		assertTrue(singleTransaction.has("actions"));
		var actions = singleTransaction.getJSONArray("actions");
		assertEquals(1, actions.length());

		var singleAction = actions.getJSONObject(0);
		assertEquals("Other", singleAction.getString("type"));
	}

	private Result<TokenDefinitionRecord> buildNativeToken() {
		return buildToken("XRD");
	}

	private Result<TokenDefinitionRecord> buildToken(String name) {
		return Result.ok(
			TokenDefinitionRecord.create(
				name, RRI.of(KNOWN_ADDRESS, name), name + " " + name, UInt256.ONE, UInt256.EIGHT,
				"http://" + name.toLowerCase() + ".icon.url", "http://" + name.toLowerCase() + "home.url",
				false
			));
	}
}