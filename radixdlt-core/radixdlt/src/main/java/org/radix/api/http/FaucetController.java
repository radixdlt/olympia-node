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

package org.radix.api.http;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.application.faucet.FaucetRequest;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.*;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class FaucetController implements Controller {
	private final EventDispatcher<FaucetRequest> faucetRequestDispatcher;

	@Inject
	public FaucetController(final EventDispatcher<FaucetRequest> faucetRequestDispatcher) {
		this.faucetRequestDispatcher = faucetRequestDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/faucet/request", this::handleFaucetRequest);
	}

	@VisibleForTesting
	void handleFaucetRequest(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBodyAsync(exchange, values -> {
			var params = values.getJSONObject("params");
			var addressString = params.getString("address");
			final RadixAddress address;
			try {
				address = RadixAddress.from(addressString);
			} catch (IllegalArgumentException e) {
				respond(exchange, jsonObject().put("error", jsonObject().put("message", "Bad address.")));
				return;
			}

			var request = FaucetRequest.create(
				address,
				aid -> respond(exchange, jsonObject().put("result", aid.toString())),
				error -> respond(exchange, jsonObject().put("error", jsonObject().put("message", error)))
			);

			faucetRequestDispatcher.dispatch(request);
		});
	}
}
