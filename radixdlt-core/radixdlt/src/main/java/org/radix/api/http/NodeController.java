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

import com.google.common.annotations.VisibleForTesting;
import com.radixdlt.application.ValidatorRegistration;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;

import java.io.IOException;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsync;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public class NodeController {
	private final RadixAddress selfAddress;
	private final EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher;

	public NodeController(RadixAddress selfAddress, EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher) {
		this.selfAddress = selfAddress;
		this.validatorRegistrationEventDispatcher = validatorRegistrationEventDispatcher;
	}

	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/node/validator", this::handleValidatorRegistration);
		handler.get("/node", this::respondWithNode);
	}

	@VisibleForTesting
	void respondWithNode(HttpServerExchange exchange) {
		respond(exchange, jsonObject().put("address", selfAddress));
	}

	@VisibleForTesting
	void handleValidatorRegistration(HttpServerExchange exchange) {
		withBodyAsync(exchange, values -> {
			boolean enabled = values.getBoolean("enabled");
			validatorRegistrationEventDispatcher.dispatch(
				enabled ? ValidatorRegistration.register() : ValidatorRegistration.unregister()
			);
		});
	}
}
