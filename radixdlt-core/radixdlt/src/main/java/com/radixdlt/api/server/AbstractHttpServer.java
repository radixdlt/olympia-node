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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.environment.ModuleRunner;
import com.radixdlt.api.Controller;
import com.radixdlt.properties.RuntimeProperties;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.StatusCodes;

import static java.util.logging.Logger.getLogger;

public class AbstractHttpServer implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();
	private static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";

	private final Map<String, Controller> controllers;
	private final String name;
	private final int port;
	private final String bindAddress;

	private Undertow server;

	public AbstractHttpServer(Map<String, Controller> controllers, RuntimeProperties properties, String name, int defaultPort) {
		this.controllers = controllers;
		this.name = name.toLowerCase(Locale.US);
		this.port = properties.get("api." + name + ".port", defaultPort);
		this.bindAddress = properties.get("api." + name + ".bind.address", DEFAULT_BIND_ADDRESS);
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
			.addHttpListener(port, bindAddress)
			.setHandler(configureRoutes())
			.build();
		server.start();

		log.info("Starting {} HTTP Server at {}:{}", name.toUpperCase(Locale.US), bindAddress, port);
	}

	@Override
	public void stop() {
		server.stop();
	}

	private HttpHandler configureRoutes() {
		var handler = Handlers.routing(true); // add path params to query params with this flag

		controllers.forEach((root, controller) -> {
			log.info("Configuring routes under {}", root);
			controller.configureRoutes(root, handler);
		});

		handler.setFallbackHandler(AbstractHttpServer::fallbackHandler);
		handler.setInvalidMethodHandler(AbstractHttpServer::invalidMethodHandler);

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
