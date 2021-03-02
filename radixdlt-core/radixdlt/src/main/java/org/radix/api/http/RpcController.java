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

import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;
import org.radix.api.services.LedgerService;
import org.radix.api.services.NetworkService;
import org.radix.api.services.SystemService;

import io.undertow.Handlers;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.respondAsync;

public class RpcController {
	private final RadixJsonRpcServer jsonRpcServer;
	private final RadixHttpWebsocketHandler websocketHandler;

	public RpcController(
		AtomsService atomsService,
		NetworkService networkService,
		SystemService systemService,
		LedgerService ledgerService
	) {
		this.jsonRpcServer = new RadixJsonRpcServer(
			atomsService,
			networkService,
			systemService,
			ledgerService
		);

		this.websocketHandler = new RadixHttpWebsocketHandler(jsonRpcServer, atomsService);
	}

	public void configureRoutes(RoutingHandler handler) {
		handler.post("/rpc", this::handleRpc);
		handler.post("/rpc/", this::handleRpc);

		handler.get("/rpc", Handlers.websocket(websocketHandler));
	}


	private void handleRpc(HttpServerExchange exchange) {
		respondAsync(exchange, () -> jsonRpcServer.handleJsonRpc(exchange));
	}
}
