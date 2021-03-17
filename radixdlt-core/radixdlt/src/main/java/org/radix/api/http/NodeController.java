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
import com.google.inject.Inject;
import com.radixdlt.application.validator.ValidatorRegistration;
import com.radixdlt.atom.LedgerAtom;
import com.radixdlt.chaos.mempoolfiller.InMemoryWallet;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsync;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class NodeController implements Controller {
	private final RadixAddress selfAddress;
	private final RadixEngine<LedgerAtom> radixEngine;
	private final EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher;

	@Inject
	public NodeController(
		@Self RadixAddress selfAddress,
		RadixEngine<LedgerAtom> radixEngine,
		EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher
	) {
		this.selfAddress = selfAddress;
		this.radixEngine = radixEngine;
		this.validatorRegistrationEventDispatcher = validatorRegistrationEventDispatcher;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/node/validator", this::handleValidatorRegistration);
		handler.get("/node", this::respondWithNode);
	}

	@VisibleForTesting
	void respondWithNode(HttpServerExchange exchange) {
		var wallet = radixEngine.getComputedState(InMemoryWallet.class);
		respond(exchange, jsonObject()
			.put("address", selfAddress)
			.put("balance", wallet.getBalance())
			.put("numParticles", wallet.getNumParticles()));
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
