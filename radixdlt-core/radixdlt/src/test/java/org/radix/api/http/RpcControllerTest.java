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
package org.radix.api.http;

import org.junit.Test;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.jsonrpc.handler.NetworkHandler;
import org.radix.api.jsonrpc.handler.SystemHandler;

import java.util.Map;
import io.undertow.server.RoutingHandler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class RpcControllerTest {
	private final SystemHandler systemHandler = mock(SystemHandler.class);
	private final NetworkHandler networkHandler = mock(NetworkHandler.class);
	private final RadixJsonRpcServer jsonRpcServer =
		new RadixJsonRpcServer(systemHandler, networkHandler, Map.of());
	private final RpcController rpcController = new RpcController(jsonRpcServer);

	@Test
	public void routesAreConfigured() {
		var handler = mock(RoutingHandler.class);

		rpcController.configureRoutes(handler);

		verify(handler).post(eq("/rpc"), any());
		verify(handler).post(eq("/rpc/"), any());
	}
}
