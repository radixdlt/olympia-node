/* Copyright 2021 Radix Publishing Ltd incorporated in Jersey (Channel Islands).
 *
 * Licensed under the Radix License, Version 1.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at:
 *
 * radixfoundation.org/licenses/LICENSE-v1
 *
 * The Licensor hereby grants permission for the Canonical version of the Work to be
 * published, distributed and used under or by reference to the Licensor’s trademark
 * Radix ® and use of any unregistered trade names, logos or get-up.
 *
 * The Licensor provides the Work (and each Contributor provides its Contributions) on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied,
 * including, without limitation, any warranties or conditions of TITLE, NON-INFRINGEMENT,
 * MERCHANTABILITY, or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Whilst the Work is capable of being deployed, used and adopted (instantiated) to create
 * a distributed ledger it is your responsibility to test and validate the code, together
 * with all logic and performance of that code under all foreseeable scenarios.
 *
 * The Licensor does not make or purport to make and hereby excludes liability for all
 * and any representation, warranty or undertaking in any form whatsoever, whether express
 * or implied, to any entity or person, including any representation, warranty or
 * undertaking, as to the functionality security use, value or other characteristics of
 * any distributed ledger nor in respect the functioning or value of any tokens which may
 * be created stored or transferred using the Work. The Licensor does not warrant that the
 * Work or any use of the Work complies with any law or regulation in any territory where
 * it may be implemented or used or that it will be appropriate for any specific purpose.
 *
 * Neither the licensor nor any current or former employees, officers, directors, partners,
 * trustees, representatives, agents, advisors, contractors, or volunteers of the Licensor
 * shall be liable for any direct or indirect, special, incidental, consequential or other
 * losses of any kind, in tort, contract or otherwise (including but not limited to loss
 * of revenue, income or profits, or loss of use or data, or loss of reputation, or loss
 * of any economic or other opportunity of whatsoever nature or howsoever arising), arising
 * out of or in connection with (without limitation of any use, misuse, of any ledger system
 * or use made or its functionality or any performance or operation of any code or protocol
 * caused by bugs or programming or logic errors or otherwise);
 *
 * A. any offer, purchase, holding, use, sale, exchange or transmission of any
 * cryptographic keys, tokens or assets created, exchanged, stored or arising from any
 * interaction with the Work;
 *
 * B. any failure in a transmission or loss of any token or assets keys or other digital
 * artefacts due to errors in transmission;
 *
 * C. bugs, hacks, logic errors or faults in the Work or any communication;
 *
 * D. system software or apparatus including but not limited to losses caused by errors
 * in holding or transmitting tokens by any third-party;
 *
 * E. breaches or failure of security including hacker attacks, loss or disclosure of
 * password, loss of private key, unauthorised use or misuse of such passwords or keys;
 *
 * F. any losses including loss of anticipated savings or other benefits resulting from
 * use of the Work or any changes to the Work (however implemented).
 *
 * You are solely responsible for; testing, validating and evaluation of all operation
 * logic, functionality, security and appropriateness of using the Work for any commercial
 * or non-commercial purpose and for any reproduction or redistribution by You of the
 * Work. You assume all risks associated with Your use of the Work and the exercise of
 * permissions under this License.
 */

package com.radixdlt.api.server;

import com.radixdlt.counters.SystemCounters;
import io.undertow.server.handlers.RequestLimitingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.ModuleRunner;
import com.radixdlt.api.Controller;
import com.radixdlt.properties.RuntimeProperties;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
	private static final int MAXIMUM_CONCURRENT_REQUESTS = Runtime.getRuntime().availableProcessors() * 8; // same as workerThreads = ioThreads * 8
	private static final int QUEUE_SIZE = 2000;

	private final Map<String, Controller> controllers;
	private final String name;
	private final int port;
	private final String bindAddress;
	private final SystemCounters counters;

	private Undertow server;

	public AbstractHttpServer(
		Map<String, Controller> controllers,
		RuntimeProperties properties,
		String name,
		int defaultPort,
		SystemCounters counters
	) {
		this.controllers = controllers;
		this.name = name.toLowerCase(Locale.US);
		this.port = properties.get("api." + name + ".port", defaultPort);
		this.bindAddress = properties.get("api." + name + ".bind.address", DEFAULT_BIND_ADDRESS);
		this.counters = Objects.requireNonNull(counters);
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
		final var handler = new MetricsHandler(
			counters,
			name,
			new RequestLimitingHandler(
				MAXIMUM_CONCURRENT_REQUESTS,
				QUEUE_SIZE,
				configureRoutes()
			)
		);

		server = Undertow.builder()
			.addHttpListener(port, bindAddress)
			.setHandler(handler)
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
