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

import org.json.JSONObject;
import org.radix.api.services.SystemService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.universe.Universe;

import java.util.Optional;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.respond;
import static org.radix.api.http.RestUtils.withBodyAsyncAndDefaultResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

public final class SystemController implements Controller {
	private final SystemService systemService;
	private final InMemorySystemInfo inMemorySystemInfo;
	private final boolean enableTestRoutes;

	@Inject
	public SystemController(
		SystemService systemService,
		InMemorySystemInfo inMemorySystemInfo,
		Universe universe
	) {
		this.systemService = systemService;
		this.inMemorySystemInfo = inMemorySystemInfo;
		this.enableTestRoutes = universe.isDevelopment() || universe.isTest();
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		// System routes
		handler.get("/api/system", this::respondWithLocalSystem);
		// keep-alive route
		handler.get("/api/ping", this::respondWithPong);
		// BFT routes
		handler.put("/api/bft/0", this::handleBftState);
		// Universe routes
		handler.get("/api/universe", this::respondWithUniverse);
		// if we are in a development universe, add the dev only routes
		if (enableTestRoutes) {
			handler.get("/api/vertices/highestqc", this::respondWithHighestQC);
		}
	}

	@VisibleForTesting
	void respondWithLocalSystem(final HttpServerExchange exchange) {
		respond(exchange, systemService.getLocalSystem());
	}

	@VisibleForTesting
	void respondWithPong(final HttpServerExchange exchange) {
		respond(exchange, systemService.getPong());
	}

	@VisibleForTesting
	void handleBftState(HttpServerExchange exchange) {
		withBodyAsyncAndDefaultResponse(exchange, values -> {
			if (values.getBoolean("state")) {
				respond(exchange, systemService.bftStart());
			} else {
				respond(exchange, systemService.bftStop());
			}
		});
	}

	@VisibleForTesting
	void respondWithUniverse(final HttpServerExchange exchange) {
		respond(exchange, systemService.getUniverse());
	}

	@VisibleForTesting
	void respondWithHighestQC(final HttpServerExchange exchange) {
		var highestQCJson = Optional.ofNullable(inMemorySystemInfo.getHighestQC())
			.map(this::formatHighestQC)
			.orElse(jsonObject().put("error", "no qc"));

		respond(exchange, highestQCJson);
	}

	private JSONObject formatHighestQC(final QuorumCertificate qc) {
		return jsonObject()
			.put("epoch", qc.getEpoch())
			.put("view", qc.getView())
			.put("vertexId", qc.getProposed().getVertexId());
	}

	private JSONObject mapSingleVertex(final VerifiedVertex v) {
		return jsonObject()
			.put("epoch", v.getParentHeader().getLedgerHeader().getEpoch())
			.put("view", v.getView().number())
			.put("hash", v.getId().toString());
	}
}
