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

import com.radixdlt.api.store.ValidatorUptime;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import org.json.JSONObject;
import org.junit.Test;

import com.radixdlt.api.data.ValidatorInfoDetails;
import com.radixdlt.api.service.ValidatorInfoService;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.utils.UInt256;
import com.radixdlt.utils.functional.Result;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonArray;
import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.utils.functional.Tuple.tuple;

public class ArchiveValidationHandlerTest {
	private static final ECPublicKey V1 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V2 = ECKeyPair.generateNew().getPublicKey();
	private static final ECPublicKey V3 = ECKeyPair.generateNew().getPublicKey();

	private final Addressing addressing = Addressing.ofNetwork(Network.LOCALNET);
	private final ValidatorInfoService validatorInfoService = mock(ValidatorInfoService.class);
	private final ArchiveValidationHandler handler = new ArchiveValidationHandler(validatorInfoService, addressing);

	@Test
	public void testValidatorsPositional() {
		var key = Optional.of(V3);

		var validators = List.of(
			createValidator(V1, "v1", UInt256.FIVE),
			createValidator(V2, "v2", UInt256.TWO),
			createValidator(V3, "v3", UInt256.SEVEN)
		);

		when(validatorInfoService.getValidators(eq(10), eq(Optional.empty())))
			.thenReturn(() -> Result.ok(tuple(key, validators)));

		var params = jsonArray().put(10);
		var response = handler.handleValidatorsGetNextEpochSet(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));

		var cursor = result.getString("cursor");
		assertEquals(cursor, key.map(addressing.forValidators()::of).map(Objects::toString).orElseThrow());

		assertTrue(result.has("validators"));
		var list = result.getJSONArray("validators");
		assertEquals(3, list.length());

		assertEquals(UInt256.FIVE, list.getJSONObject(0).get("totalDelegatedStake"));
		assertEquals("v1", list.getJSONObject(0).get("name"));

		assertEquals(UInt256.TWO, list.getJSONObject(1).get("totalDelegatedStake"));
		assertEquals("v2", list.getJSONObject(1).get("name"));

		assertEquals(UInt256.SEVEN, list.getJSONObject(2).get("totalDelegatedStake"));
		assertEquals("v3", list.getJSONObject(2).get("name"));
	}

	@Test
	public void testValidatorsNamed() {
		var key = Optional.of(V3);

		var validators = List.of(
			createValidator(V1, "v1", UInt256.FIVE),
			createValidator(V2, "v2", UInt256.TWO),
			createValidator(V3, "v3", UInt256.SEVEN)
		);

		when(validatorInfoService.getValidators(eq(10), eq(Optional.empty())))
			.thenReturn(() -> Result.ok(tuple(key, validators)));

		var params = jsonObject().put("size", 10);
		var response = handler.handleValidatorsGetNextEpochSet(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertTrue(result.has("cursor"));

		var cursor = result.getString("cursor");
		assertEquals(cursor, key.map(addressing.forValidators()::of).map(Objects::toString).orElseThrow());

		assertTrue(result.has("validators"));
		var list = result.getJSONArray("validators");
		assertEquals(3, list.length());

		assertEquals(UInt256.FIVE, list.getJSONObject(0).get("totalDelegatedStake"));
		assertEquals("v1", list.getJSONObject(0).get("name"));

		assertEquals(UInt256.TWO, list.getJSONObject(1).get("totalDelegatedStake"));
		assertEquals("v2", list.getJSONObject(1).get("name"));

		assertEquals(UInt256.SEVEN, list.getJSONObject(2).get("totalDelegatedStake"));
		assertEquals("v3", list.getJSONObject(2).get("name"));
	}

	@Test
	public void testLookupValidatorPositional() {
		when(validatorInfoService.getValidator(eq(V1)))
			.thenReturn(Result.ok(createValidator(V1, "v1", UInt256.FIVE)));

		var params = jsonArray().put(addressing.forValidators().of(V1));
		var response = handler.handleValidatorsLookupValidator(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertNotNull(result);

		assertEquals(UInt256.FIVE, result.get("totalDelegatedStake"));
		assertEquals("http://v1.com", result.get("infoURL"));
		assertEquals("v1", result.get("name"));
	}

	@Test
	public void testLookupValidatorNamed() {
		when(validatorInfoService.getValidator(eq(V1)))
			.thenReturn(Result.ok(createValidator(V1, "v1", UInt256.FIVE)));

		var params = jsonObject().put("validatorAddress", addressing.forValidators().of(V1));
		var response = handler.handleValidatorsLookupValidator(requestWith(params));

		assertNotNull(response);
		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertNotNull(result);

		assertEquals(UInt256.FIVE, result.get("totalDelegatedStake"));
		assertEquals("http://v1.com", result.get("infoURL"));
		assertEquals("v1", result.get("name"));
	}

	private ValidatorInfoDetails createValidator(ECPublicKey v1, String name, UInt256 stake) {
		return ValidatorInfoDetails.create(
			v1, REAddr.ofPubKeyAccount(v1),
			name, "http://" + name + ".com",
			stake, UInt256.ZERO,
			true,
			true,
			10,
			ValidatorUptime.empty()
		);
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}
}