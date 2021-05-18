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

package com.radixdlt.api.server;

import com.google.inject.Inject;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.Controller;
import com.radixdlt.api.qualifier.AtNode;
import com.radixdlt.properties.RuntimeProperties;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.Set;
import java.util.logging.Level;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.StatusCodes;

import static java.util.logging.Logger.getLogger;

/**
 * Radix Node API
 */
public final class NodeHttpServer implements ModuleRunner {
	private static final int DEFAULT_PORT = 3333;
	private final int port;
	private final Set<Controller> controllers;

	private Undertow server;

	@Inject
	public NodeHttpServer(
		@AtNode Set<Controller> controllers,
		RuntimeProperties properties
	) {
		//TODO: perhaps rename properties to match other namings?
		this.port = properties.get("node_api.port", DEFAULT_PORT);
		this.controllers = controllers;
	}

	private static void fallbackHandler(HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.NOT_FOUND);
		exchange.getResponseSender().send(
			"No matching path found for " + exchange.getRequestMethod() + " " + exchange.getRequestPath()
		);
	}

	private static void invalidMethodHandler(HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.NOT_ACCEPTABLE);
		exchange.getResponseSender().send(
			"Invalid method, path exists for " + exchange.getRequestMethod() + " " + exchange.getRequestPath()
		);
	}

	@Override
	public void start() {
		server = Undertow.builder()
			.addHttpListener(port, "0.0.0.0")
			.setHandler(configureRoutes())
			.build();
		server.start();
	}

	@Override
	public void stop() {
		this.server.stop();
	}

	private HttpHandler configureRoutes() {
		var handler = Handlers.routing(true); // add path params to query params with this flag

		controllers.forEach(c -> c.configureRoutes(handler));
		handler.setFallbackHandler(NodeHttpServer::fallbackHandler);
		handler.setInvalidMethodHandler(NodeHttpServer::invalidMethodHandler);

		return wrapWithCorsFilter(handler);
	}

	private Filter wrapWithCorsFilter(final RoutingHandler handler) {
		var filter = new Filter(handler);

		// Disable INFO logging for CORS filter, as it's a bit distracting
		getLogger(filter.getClass().getName()).setLevel(Level.WARNING);
		filter.setPolicyClass(AllowAll.class.getName());
		filter.setUrlPattern("^.*$");

		return filter;
	}
}
