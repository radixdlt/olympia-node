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

package com.radixdlt.api.controller;

import org.junit.Test;

import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

import io.undertow.server.RoutingHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class FaucetControllerTest {
	private final JsonRpcServer jsonRpcServer = new JsonRpcServer(Map.of());
	private final FaucetController controller = new FaucetController(jsonRpcServer);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);

		controller.configureRoutes(handler);

		verify(handler).post(eq("/faucet"), any());
	}
}