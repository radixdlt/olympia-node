package org.radix.api.jsonrpc;/*
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

import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.jsonrpc.handler.HighLevelApiHandler;
import org.radix.api.services.HighLevelApiService;

import com.radixdlt.identifiers.RadixAddress;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class HighLevelApiHandlerTest {
	public static final String KNOWN_ADDRESS = "JH1P8f3znbyrDj8F4RWpix7hRkgxqHjdW2fNnKpR3v6ufXnknor";

	//TODO: test methods below should be removed once stubs will be replaced with real implementations
	@Test
	public void stubNativeToken() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		when(service.getAddress()).thenReturn(RadixAddress.from(KNOWN_ADDRESS));


		var result = handler.handleNativeToken(jsonObject().put("id", "1"));
		assertNotNull(result);
	}

	@Test
	public void stubTokenBalances() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		var params = jsonObject().put("address", KNOWN_ADDRESS);
		var result = handler.handleTokenBalances(jsonObject().put("id", "1").put("params", params));

		assertNotNull(result);
	}

	@Test
	public void stubExecutedTransactions() {
		var service = mock(HighLevelApiService.class);
		var handler = new HighLevelApiHandler(service);

		var params = jsonObject().put("address", KNOWN_ADDRESS).put("size", 5);
		var result = handler.handleExecutedTransactions(jsonObject().put("id", "1").put("params", params));

		assertNotNull(result);
	}
}