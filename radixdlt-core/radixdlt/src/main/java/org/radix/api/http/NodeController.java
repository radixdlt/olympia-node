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
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.application.validator.ValidatorRegistration;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;

import com.radixdlt.statecomputer.LedgerAndBFTProof;
import com.radixdlt.utils.UInt256;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.*;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class NodeController implements Controller {
	private final RadixAddress selfAddress;
	private final RadixEngine<LedgerAndBFTProof> radixEngine;
	private final EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher;

	@Inject
	public NodeController(
		@Self RadixAddress selfAddress,
		RadixEngine<LedgerAndBFTProof> radixEngine,
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
		var particleCount = radixEngine.getComputedState(Integer.class);
		var balance = radixEngine.getComputedState(UInt256.class);
		respond(exchange, jsonObject()
			.put("address", selfAddress)
			.put("balance", TokenUnitConversions.subunitsToUnits(balance))
			.put("numParticles", particleCount));
	}

	@VisibleForTesting
	void handleValidatorRegistration(HttpServerExchange exchange) {
		// TODO: implement JSON-RPC 2.0 specification
		withBodyAsync(exchange, values -> {
			boolean enabled = values.getBoolean("enabled");
			var registration = ValidatorRegistration.create(
				enabled,
				aid -> respond(exchange, jsonObject().put("result", aid.toString())),
				error -> respond(exchange, jsonObject().put("error", jsonObject().put("message", error)))
			);

			validatorRegistrationEventDispatcher.dispatch(registration);
		});
	}
}
