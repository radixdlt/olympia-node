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

import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.AccountService;
import com.radixdlt.api.service.ValidatorInfoService;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.identifiers.ValidatorAddress;
import com.radixdlt.utils.UInt256;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class ValidationHandlerTest {
	private static final ECPublicKey V1 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V2 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V3 = ECKeyPair.generateNew().getPublicKey();

	public static final JSONObject EMPTY_REQUEST = jsonObject().put("id", "1").put("params", jsonArray());

	private final AccountService accountService = mock(AccountService.class);
	private final ValidatorInfoService validatorInfoService = mock(ValidatorInfoService.class);
	private final ValidationHandler handler = new ValidationHandler(accountService, validatorInfoService);

	@Test
	public void testHandleGetNodeInfo() {
		var validatorInfo = jsonObject()
			.put("address", ValidatorAddress.of(V1))
			.put("name", "validator 1")
			.put("url", "https://validator1.com/")
			.put("registered", true)
			.put("stakes", jsonArray())
			.put("totalStake", UInt256.FIVE);

		when(accountService.getValidatorInfo())
			.thenReturn(validatorInfo);

		var response = handler.handleGetNodeInfo(EMPTY_REQUEST);
		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertEquals(ValidatorAddress.of(V1), result.get("address"));
		assertEquals("validator 1", result.get("name"));
		assertEquals(true, result.get("registered"));
		assertEquals(UInt256.FIVE, result.get("totalStake"));
		assertEquals("https://validator1.com/", result.get("url"));
	}


	private ValidatorInfoDetails createValidator(ECPublicKey v1, String name, UInt256 stake) {
		return ValidatorInfoDetails.create(
			v1, REAddr.ofPubKeyAccount(v1),
			name, "http://" + name + ".com",
			stake, UInt256.ZERO,
			true
		);
	}
}
