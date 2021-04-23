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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.respondAsync;

public final class RpcController implements Controller {
	private final RadixJsonRpcServer jsonRpcServer;

	@Inject
	public RpcController(RadixJsonRpcServer jsonRpcServer) {
		this.jsonRpcServer = jsonRpcServer;
	}

	@Override
	public void configureRoutes(RoutingHandler handler) {
		handler.post("/rpc", this::handleRpc);
		handler.post("/rpc/", this::handleRpc);
	}

	@VisibleForTesting
	void handleRpc(HttpServerExchange exchange) {
		respondAsync(exchange, () -> jsonRpcServer.handleJsonRpc(exchange));
	}
}
