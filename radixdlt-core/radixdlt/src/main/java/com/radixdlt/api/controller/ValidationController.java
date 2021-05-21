/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.api.controller;

import com.radixdlt.api.Controller;
import com.radixdlt.api.server.JsonRpcServer;

import io.undertow.server.RoutingHandler;

public final class ValidationController implements Controller {
	private final JsonRpcServer jsonRpcServer;

	public ValidationController(JsonRpcServer jsonRpcServer) {
		this.jsonRpcServer = jsonRpcServer;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/validation", jsonRpcServer::handleHttpRequest);
		handler.post("/validation/", jsonRpcServer::handleHttpRequest);
	}
	//TODO: check adding fee in other places
	/*
	void handleExecute(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBody(exchange, values -> {
			try {
				var actionsArray = values.getJSONArray("actions");
				var actions = new ArrayList<TxAction>();
				actions.add(new PayFee(account, TokenFeeChecker.FIXED_FEE));
				for (int i = 0; i < actionsArray.length(); i++) {
					var actionObject = actionsArray.getJSONObject(i);
					var txAction = parseAction(actionObject);
					actions.add(txAction);
				}
				var completableFuture = new CompletableFuture<MempoolAddSuccess>();
				var request = NodeApplicationRequest.create(actions, completableFuture);
				nodeApplicationRequestEventDispatcher.dispatch(request);

				var success = completableFuture.get();
				respond(exchange, jsonObject()
					.put("result", jsonObject()
						.put("transaction", Hex.toHexString(success.getTxn().getPayload()))
						.put("transaction_identifier", success.getTxn().getId().toString())
					)
				);
			} catch (ExecutionException | RuntimeException e) {
				respond(exchange, jsonObject()
					.put("error", jsonObject()
					.put("message", e.getCause().getMessage()))
				);
			}
		});
	}
	 */
}
