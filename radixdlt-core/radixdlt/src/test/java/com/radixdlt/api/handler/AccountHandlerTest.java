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

package com.radixdlt.api.handler;

import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.application.Balances;
import com.radixdlt.application.StakedBalance;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class AccountHandlerTest {
	private final ClientApiStore clientApiStore = mock(ClientApiStore.class);
	private final SubmissionService submissionService = mock(SubmissionService.class);
	private final ActionParserService actionParserService = new ActionParserService(clientApiStore);
	private final RadixEngine<LedgerAndBFTProof> radixEngine = mock(RadixEngine.class);
	private final ECKeyPair keyPair = ECKeyPair.generateNew();
	private final ECPublicKey bftKey = keyPair.getPublicKey();
	private final ECPublicKey delegate = ECKeyPair.generateNew().getPublicKey();
	private final HashSigner hashSigner = keyPair::sign;
	private final REAddr rriAddress = REAddr.ofHashedKey(ECKeyPair.generateNew().getPublicKey(), "wsx");

	private final AccountHandler handler = new AccountHandler(
		submissionService, actionParserService, radixEngine, hashSigner, bftKey, clientApiStore
	);

	@Test
	public void testHandleAccountGetInfo() {
		var balances = new Balances();
		balances.add(rriAddress, UInt256.EIGHT);

		when(radixEngine.getComputedState(Balances.class))
			.thenReturn(balances);

		var stakeBalances = new StakedBalance();
		stakeBalances.addStake(delegate, UInt256.THREE);

		when(radixEngine.getComputedState(StakedBalance.class))
			.thenReturn(stakeBalances);

		when(clientApiStore.getTokenDefinition(rriAddress)).thenReturn(buildToken("wsx"));

		var response = handler.handleAccountGetInfo(requestWith(jsonObject()));
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");
		assertNotNull(result);

		assertTrue(result.has("address"));
		assertTrue(result.has("balance"));

		var balance = result.getJSONObject("balance");

		assertTrue(balance.has("tokens"));
		assertTrue(balance.has("stakes"));

		var tokens = balance.getJSONArray("tokens");
		var stakes = balance.getJSONArray("stakes");

		assertNotNull(tokens);
		assertNotNull(stakes);
		assertEquals(1, tokens.length());
		assertEquals(1, stakes.length());

		var tokenBalance0 = tokens.getJSONObject(0);
		var stakeBalance0 = stakes.getJSONObject(0);
		assertNotNull(tokenBalance0);
		assertNotNull(stakeBalance0);

		assertTrue(tokenBalance0.has("amount"));
		assertTrue(stakeBalance0.has("amount"));

		assertEquals(UInt384.EIGHT, tokenBalance0.get("amount"));
		assertEquals(UInt256.THREE, stakeBalance0.get("amount"));
	}

	@Test
	public void testHandleAccountSubmitTransactionSingleStep() {
		var aid = AID.from(HashUtils.random256().asBytes());

		when(submissionService.oneStepSubmit(any(), any(), any()))
			.thenReturn(Result.ok(aid));

		var actions = jsonArray()
			.put(
				jsonObject()
					.put("type", "RegisterValidator")
					.put("validator", ValidatorAddress.of(bftKey))
			);
		var params = jsonArray()
			.put(actions)
			.put("message");

		var response = handler.handleAccountSubmitTransactionSingleStep(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertNotNull(result);
		assertEquals(aid, result.get("txID"));
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}

	private Result<TokenDefinitionRecord> buildToken(String name) {
		return Result.ok(
			TokenDefinitionRecord.create(
				name, name, rriAddress, name + " " + name, UInt384.EIGHT,
				"http://" + name.toLowerCase() + ".icon.url", "http://" + name.toLowerCase() + "home.url",
				false
			));
	}
}
