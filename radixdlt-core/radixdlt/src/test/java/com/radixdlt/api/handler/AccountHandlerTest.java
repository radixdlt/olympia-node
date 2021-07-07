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

import com.radixdlt.consensus.bft.View;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.statecomputer.forks.Forks;
import com.radixdlt.statecomputer.forks.RERules;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.radixdlt.api.service.AccountInfoService;
import com.radixdlt.api.service.ActionParserService;
import com.radixdlt.api.service.SubmissionService;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.functional.Result;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class AccountHandlerTest {
	private final SubmissionService submissionService = mock(SubmissionService.class);
	private final AccountInfoService accountService = mock(AccountInfoService.class);
	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final Forks forks = mock(Forks.class);
	private final ActionParserService actionParserService = new ActionParserService(addressing, forks);

	private final ECKeyPair keyPair = ECKeyPair.generateNew();
	private final ECPublicKey bftKey = keyPair.getPublicKey();
	private final HashSigner hashSigner = keyPair::sign;

	private final AccountHandler handler = new AccountHandler(
		accountService, submissionService, actionParserService,
		hashSigner, REAddr.ofPubKeyAccount(keyPair.getPublicKey())
	);

	@Before
	public void setup() {
		final var reRules = mock(RERules.class);
		when(reRules.getMaxRounds()).thenReturn(View.of(10L));
		when(forks.getCandidateFork()).thenReturn(Optional.empty());
	}

	@Test
	public void testHandleAccountGetInfo() {
		when(accountService.getAccountInfo())
			.thenReturn(
				jsonObject()
					.put("address", "some address")
					.put("balance", jsonObject()
						.put("tokens", jsonArray())
						.put("stakes", jsonArray()))
			);

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
		assertEquals(0, tokens.length());
		assertEquals(0, stakes.length());
	}

	@Test
	public void testHandleAccountSubmitTransactionSingleStep() {
		var aid = AID.from(HashUtils.random256().asBytes());

		when(submissionService.oneStepSubmit(any(), any(), any(), any(), eq(false)))
			.thenReturn(Result.ok(aid));

		var actions = jsonArray()
			.put(
				jsonObject()
					.put("type", "RegisterValidator")
					.put("validator", addressing.forValidators().of(bftKey))
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
}
