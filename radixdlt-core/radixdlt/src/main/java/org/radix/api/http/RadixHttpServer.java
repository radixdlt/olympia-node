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

import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;
import org.radix.api.services.LedgerService;
import org.radix.api.services.NetworkService;
import org.radix.api.services.SystemService;
import org.radix.universe.system.LocalSystem;

import com.google.inject.Inject;
import com.radixdlt.ModuleRunner;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.universe.Universe;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.Map;
import java.util.logging.Level;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.StatusCodes;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;

/**
 * Radix REST API
 */
//TODO: switch to Netty
public final class RadixHttpServer {
	public static final int DEFAULT_PORT = 8080;

	private final AtomsService atomsService;

	private final SystemController systemController;
	private final NetworkController networkController;
	private final int port;
	private final ChaosController chaosController;
	private final RpcController rpcController;
	private final NodeController nodeController;

	private Undertow server;

	@Inject(optional = true)
	@MempoolFillerKey
	private RadixAddress mempoolFillerAddress;

	@Inject
	public RadixHttpServer(
		AtomsService atomsService,
		InMemorySystemInfo inMemorySystemInfo,
		Map<String, ModuleRunner> moduleRunners,
		RadixEngine<LedgerAtom> radixEngine,
		LedgerEntryStore store,
		NodeController nodeController,
		ChaosController chaosController,
		Universe universe,
		Serialization serialization,
		RuntimeProperties properties,
		LocalSystem localSystem,
		AddressBook addressBook,
		Hasher hasher
	) {
		requireNonNull(inMemorySystemInfo);
		requireNonNull(serialization);
		requireNonNull(localSystem);
		requireNonNull(universe);
		requireNonNull(radixEngine);

		this.atomsService = atomsService;

		var consensusRunner = requireNonNull(moduleRunners.get("consensus"));
		var systemService = new SystemService(serialization, universe, localSystem, consensusRunner);
		var networkService = new NetworkService(serialization, localSystem, addressBook, hasher);
		var ledgerService = new LedgerService(store, serialization);

		var jsonRpcServer = new RadixJsonRpcServer(atomsService, networkService, systemService, ledgerService);
		var websocketHandler = new RadixHttpWebsocketHandler(jsonRpcServer, atomsService);

		this.port = properties.get("cp.port", DEFAULT_PORT);

		boolean enableTestRoutes = universe.isDevelopment() || universe.isTest();
		this.nodeController = nodeController;
		this.chaosController = chaosController;
		this.systemController = new SystemController(atomsService, systemService, inMemorySystemInfo, enableTestRoutes);
		this.rpcController = new RpcController(jsonRpcServer, websocketHandler);
		this.networkController = new NetworkController(networkService);
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
