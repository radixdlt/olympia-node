/*
 * (C) Copyright 2020 Radix DLT Ltd
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

import org.radix.api.services.AtomsService;

import com.google.inject.Inject;
import com.radixdlt.properties.RuntimeProperties;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.logging.Level;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.StatusCodes;

import static java.util.logging.Logger.getLogger;

/**
 * Radix REST API
 */
//TODO: switch to Netty
public final class RadixHttpServer {
	public static final int DEFAULT_PORT = 8080;

	private final SystemController systemController;
	private final NetworkController networkController;
	private final ChaosController chaosController;
	private final RpcController rpcController;
	private final NodeController nodeController;
	private final AtomsService atomsService;
	private final int port;

	private Undertow server;

	@Inject
	public RadixHttpServer(
		NodeController nodeController,
		ChaosController chaosController,
		RuntimeProperties properties,
		RpcController rpcController,
		NetworkController networkController,
		SystemController systemController,
		AtomsService atomsService
	) {
		this.port = properties.get("cp.port", DEFAULT_PORT);

		this.nodeController = nodeController;
		this.chaosController = chaosController;
		this.systemController = systemController;
		this.rpcController = rpcController;
		this.networkController = networkController;
		this.atomsService = atomsService;
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

	public void start() {
		this.atomsService.start();

		server = Undertow.builder()
			.addHttpListener(port, "0.0.0.0")
			.setHandler(configureRoutes())
			.build();

		server.start();
	}

	private HttpHandler configureRoutes() {
		var handler = Handlers.routing(true); // add path params to query params with this flag

		systemController.configureRoutes(handler);
		networkController.configureRoutes(handler);
		chaosController.configureRoutes(handler);
		rpcController.configureRoutes(handler);
		nodeController.configureRoutes(handler);

		handler.setFallbackHandler(RadixHttpServer::fallbackHandler);
		handler.setInvalidMethodHandler(RadixHttpServer::invalidMethodHandler);

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

	public void stop() {
		this.atomsService.stop();
		this.server.stop();
	}
}
