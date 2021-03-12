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

import org.radix.api.services.NetworkService;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.api.http.RestUtils.getParameter;
import static org.radix.api.http.RestUtils.respond;

public final class NetworkController {
	private final NetworkService networkService;

	@Inject
	public NetworkController(NetworkService networkService) {
		this.networkService = networkService;
	}

	public void configureRoutes(final RoutingHandler handler) {
		handler.get("/api/network", this::respondWithNetwork);
		handler.get("/api/network/peers/live", this::respondWithLivePeers);
		handler.get("/api/network/peers", this::respondWithPeers);
		handler.get("/api/network/peers/{id}", this::respondWithSinglePeer);
	}

	@VisibleForTesting
	void respondWithNetwork(final HttpServerExchange exchange) {
		respond(exchange, this.networkService.getNetwork());
	}

	@VisibleForTesting
	void respondWithLivePeers(final HttpServerExchange exchange) {
		respond(exchange, this.networkService.getLivePeers());
	}

	@VisibleForTesting
	void respondWithPeers(final HttpServerExchange exchange) {
		respond(exchange, this.networkService.getPeers());
	}

	@VisibleForTesting
	void respondWithSinglePeer(final HttpServerExchange exchange) {
		respond(exchange, this.networkService.getPeer(getParameter(exchange, "id").orElse(null)));
	}
}
