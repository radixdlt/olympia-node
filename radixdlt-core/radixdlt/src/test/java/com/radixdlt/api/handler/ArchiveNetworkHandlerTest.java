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

import com.radixdlt.api.service.NetworkInfoService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public class ArchiveNetworkHandlerTest {
	private final NetworkInfoService networkInfoService = mock(NetworkInfoService.class);
	private final ArchiveNetworkHandler handler = new ArchiveNetworkHandler(networkInfoService, 2);

	@Test
	public void testNetworkId() {
		when(networkInfoService.throughput())
			.thenReturn(123L);

		var response = handler.handleNetworkGetId(requestWith());

		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertEquals(2, result.get("networkId"));
	}

	@Test
	public void testNetworkTransactionThroughput() {
		when(networkInfoService.throughput())
			.thenReturn(123L);

		var response = handler.handleNetworkGetThroughput(requestWith());

		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertEquals(123L, result.get("tps"));
	}

	@Test
	public void testNetworkTransactionDemand() {
		when(networkInfoService.demand())
			.thenReturn(123L);

		var response = handler.handleNetworkGetDemand(requestWith());

		assertTrue(response.has("result"));

		var result = response.getJSONObject("result");

		assertEquals(123L, result.get("tps"));
	}

	private JSONObject requestWith() {
		return requestWith(null);
	}

	private JSONObject requestWith(Object params) {
		return jsonObject().put("id", "1").putOpt("params", params);
	}
}