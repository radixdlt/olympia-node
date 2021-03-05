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
package org.radix.api.jsonrpc.handler;

import org.json.JSONObject;
import org.junit.Test;
import org.radix.api.services.SystemService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class SystemHandlerTest {
	private final SystemService systemService = mock(SystemService.class);
	private final SystemHandler systemHandler = new SystemHandler(systemService);
	private final JSONObject request = jsonObject().put("id", 123);

	@Test
	public void testHandlePing() {
		when(systemService.getPong()).thenReturn(jsonObject().put("key", "0"));
		assertEquals("{\"key\":\"0\"}", systemHandler.handlePing(request).getJSONObject("result").toString());
	}

	@Test
	public void testHandleBftStart() {
		when(systemService.bftStart()).thenReturn(jsonObject().put("key", "1"));
		assertEquals("{\"key\":\"1\"}", systemHandler.handleBftStart(request).getJSONObject("result").toString());
	}

	@Test
	public void testHandleBftStop() {
		when(systemService.bftStop()).thenReturn(jsonObject().put("key", "2"));
		assertEquals("{\"key\":\"2\"}", systemHandler.handleBftStop(request).getJSONObject("result").toString());
	}

	@Test
	public void testHandleGetUniverse() {
		when(systemService.getUniverse()).thenReturn(jsonObject().put("key", "3"));
		assertEquals("{\"key\":\"3\"}", systemHandler.handleGetUniverse(request).getJSONObject("result").toString());
	}

	@Test
	public void testHandleGetLocalSystem() {
		when(systemService.getLocalSystem()).thenReturn(jsonObject().put("key", "4"));
		assertEquals("{\"key\":\"4\"}", systemHandler.handleGetLocalSystem(request).getJSONObject("result").toString());
	}
}