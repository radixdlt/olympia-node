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

import com.radixdlt.api.service.TokenService;
import com.radixdlt.api.store.TokenDefinitionRecord;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt384;
import com.radixdlt.utils.functional.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class ArchiveTokenHandlerTest {
	private static final ECPublicKey PUB_KEY = ECKeyPair.generateNew().getPublicKey();

	private final TokenService tokenService = mock(TokenService.class);
	private final ArchiveTokenHandler handler = new ArchiveTokenHandler(tokenService);

	@Test
	public void testNativeToken() {
		when(tokenService.getNativeTokenDescription())
			.thenReturn(buildNativeToken());

		var response = handler.handleTokensGetNativeToken(requestWith());
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertNotNull(result);
		assertEquals("xrd", result.getString("name"));
		assertEquals("xrd xrd", result.getString("description"));
		assertEquals(UInt384.EIGHT, result.get("currentSupply"));
	}

	@Test
	public void testTokenInfoPositional() {
		when(tokenService.getTokenDescription(any(String.class)))
			.thenReturn(buildToken("fyy"));

		var params = jsonArray().put(REAddr.ofHashedKey(PUB_KEY, "fyy").toString());
		var response = handler.handleTokensGetInfo(requestWith(params));
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertNotNull(result);
		assertEquals("fyy", result.getString("name"));
		assertEquals("fyy fyy", result.getString("description"));
		assertEquals(UInt384.EIGHT, result.get("currentSupply"));
	}

	@Test
	public void testTokenInfoNamed() {
		when(tokenService.getTokenDescription(any(String.class)))
			.thenReturn(buildToken("fyy"));

		var params = jsonObject().put("rri", REAddr.ofHashedKey(PUB_KEY, "fyy").toString());
		var response = handler.handleTokensGetInfo(requestWith(params));
		assertNotNull(response);

		var result = response.getJSONObject("result");
		assertNotNull(result);
		assertEquals("fyy", result.getString("name"));
		assertEquals("fyy fyy", result.getString("description"));
		assertEquals(UInt384.EIGHT, result.get("currentSupply"));
	}

	private JSONObject requestWith() {
		return requestWith(null);
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}

	private Result<TokenDefinitionRecord> buildNativeToken() {
		return buildToken("xrd");
	}

	private Result<TokenDefinitionRecord> buildToken(String name) {
		return Result.ok(
			TokenDefinitionRecord.create(
				name, name, REAddr.ofHashedKey(PUB_KEY, name), name + " " + name, UInt384.EIGHT,
				"http://" + name.toLowerCase() + ".icon.url", "http://" + name.toLowerCase() + "home.url",
				false
			));
	}
}
