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

import com.radixdlt.application.ValidatorRegistration;
import com.radixdlt.application.ValidatorRegistrator;
import com.radixdlt.consensus.bft.Self;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.radix.api.jsonrpc.RadixJsonRpcPeer;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;
import org.radix.api.services.NetworkService;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import com.radixdlt.ModuleRunner;
import com.radixdlt.chaos.mempoolfiller.InMemoryWallet;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerKey;
import com.radixdlt.chaos.mempoolfiller.MempoolFillerUpdate;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketProtocolHandshakeHandler;
import io.undertow.websockets.core.WebSocketChannel;

import static org.radix.api.jsonrpc.JsonRpcUtil.jsonArray;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;

import static com.radixdlt.utils.Base58.fromBase58;

/**
 * Radix REST API
 */
//TODO: eventually switch to Netty
public final class RadixHttpServer {
	public static final int DEFAULT_PORT = 8080;
	public static final String CONTENT_TYPE_JSON = "application/json";

	private static final Logger logger = LogManager.getLogger();

	private final ConcurrentHashMap<RadixJsonRpcPeer, WebSocketChannel> peers;
	private final ModuleRunner consensusRunner;
	private final AtomsService atomsService;
	private final RadixJsonRpcServer jsonRpcServer;
	private final NetworkService networkService;
	private final Universe universe;
	private final JSONObject apiSerializedUniverse;
	private final LocalSystem localSystem;
	private final Serialization serialization;
	private final InMemorySystemInfo inMemorySystemInfo;
	private final int port;
	private Undertow server;
	private final EventDispatcher<MessageFlooderUpdate> messageFloodUpdateEventDispatcher;
	private final EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher;
	private final EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher;
	private final RadixEngine<LedgerAtom> radixEngine;

	@Inject
	@Self
	private RadixAddress selfAddress;

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
		EventDispatcher<MessageFlooderUpdate> messageFloodUpdateEventDispatcher,
		EventDispatcher<MempoolFillerUpdate> mempoolFillerUpdateEventDispatcher,
		EventDispatcher<ValidatorRegistration> validatorRegistrationEventDispatcher,
		Universe universe,
		Serialization serialization,
		RuntimeProperties properties,
		LocalSystem localSystem,
		AddressBook addressBook,
		Hasher hasher
	) {
		this.inMemorySystemInfo = Objects.requireNonNull(inMemorySystemInfo);
		this.consensusRunner = Objects.requireNonNull(moduleRunners.get("consensus"));
		this.universe = Objects.requireNonNull(universe);
		this.serialization = Objects.requireNonNull(serialization);
		this.apiSerializedUniverse = serialization.toJsonObject(this.universe, DsonOutput.Output.API);
		this.localSystem = Objects.requireNonNull(localSystem);
		this.peers = new ConcurrentHashMap<>();
		this.radixEngine = Objects.requireNonNull(radixEngine);
		this.atomsService = atomsService;
		this.messageFloodUpdateEventDispatcher = messageFloodUpdateEventDispatcher;
		this.mempoolFillerUpdateEventDispatcher = mempoolFillerUpdateEventDispatcher;
		this.validatorRegistrationEventDispatcher = validatorRegistrationEventDispatcher;
		this.jsonRpcServer = new RadixJsonRpcServer(
			consensusRunner,
			serialization,
			store,
			atomsService,
			localSystem,
			addressBook,
			universe
		);
		this.networkService = new NetworkService(serialization, localSystem, addressBook, hasher);
		this.port = properties.get("cp.port", DEFAULT_PORT);
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

		// System routes
		handler.add(Methods.GET, "/api/system", this::respondWithSystem);
		handler.add(Methods.GET, "/api/system/modules/api/tasks-waiting", this::respondWaitingTaskCount);

		// Network routes
		handler.add(Methods.GET, "/api/network", this::respondWithNetwork);
		handler.add(Methods.GET, "/api/network/peers/live", this::respondWithLivePeers);
		handler.add(Methods.GET, "/api/network/peers", this::respondWithPeers);
		handler.add(Methods.GET, "/api/network/peers/{id}", this::respondWithSinglePeer);

		// Universe routes
		handler.add(Methods.GET, "/api/universe", this::respondWithUniverse);

		// BFT routes
		//TODO: potentially blocking
		handler.add(Methods.PUT, "/api/bft/0", this::handleBftState);

		// Chaos routes
		handler.add(Methods.PUT, "/api/chaos/message-flooder", this::handleMessageFlood);
		//TODO: potentially blocking
		handler.add(Methods.PUT, "/api/chaos/mempool-filler", this::handleMempoolFill);
		handler.add(Methods.GET, "/api/chaos/mempool-filler", this::respondWithMempoolFill);

		handler.add(Methods.POST, "/node/validator", this::handleValidatorRegistration);
		handler.add(Methods.GET, "/node", this::respondWithNode);

		// keep-alive route
		handler.add(Methods.GET, "/api/ping", this::respondWithPong);

		// handle POST requests
		var rpcPostHandler = new RpcPostHandler();

		// handle both /rpc and /rpc/ for usability
		//TODO: potentially blocking
		handler.add(Methods.POST, "/rpc", rpcPostHandler);
		//TODO: potentially blocking
		handler.add(Methods.POST, "/rpc/", rpcPostHandler);

		// handle websocket requests (which also uses GET method)
		handler.add(Methods.GET, "/rpc", createWebsocketHandler());

		// add appropriate error handlers for meaningful error messages (undertow is silent by default)
		handler.setFallbackHandler(RadixHttpServer::fallbackHandler);
		handler.setInvalidMethodHandler(RadixHttpServer::invalidMethodHandler);

		configureTestRoutes(handler);

		return wrapWithCorsFilter(handler);
	}

	private void configureTestRoutes(RoutingHandler handler) {
		// if we are in a development universe, add the dev only routes
		if (!this.universe.isDevelopment() && !this.universe.isTest()) {
			return;
		}

		handler.add(Methods.GET, "/api/vertices/committed", this::respondWithCommittedVertices);
		handler.add(Methods.GET, "/api/vertices/highestqc", this::respondWithHighestQC);
	}

	private Filter wrapWithCorsFilter(final RoutingHandler handler) {
		var filter = new Filter(handler);

		// Disable INFO logging for CORS filter, as it's a bit distracting
		java.util.logging.Logger.getLogger(filter.getClass().getName()).setLevel(java.util.logging.Level.WARNING);
		filter.setPolicyClass(AllowAll.class.getName());
		filter.setUrlPattern("^.*$");

		return filter;
	}

	//Non-blocking
	private void respondWithMempoolFill(HttpServerExchange exchange) {
		InMemoryWallet wallet = radixEngine.getComputedState(InMemoryWallet.class);
		respond(jsonObject()
			.put("address", mempoolFillerAddress)
			.put("balance", wallet.getBalance())
			.put("numParticles", wallet.getNumParticles()), exchange);
	}

	//Non-blocking
	private void respondWithPong(final HttpServerExchange exchange) {
		respond(jsonObject().put("response", "pong").put("timestamp", Time.currentTimestamp()), exchange);
	}

	//Non-blocking
	private void respondWithUniverse(final HttpServerExchange exchange) {
		respond(this.apiSerializedUniverse, exchange);
	}

	//Non-blocking
	private void respondWithSinglePeer(final HttpServerExchange exchange) {
		respond(this.networkService.getPeer(getParameter(exchange, "id").orElse(null)), exchange);
	}

	//Non-blocking
	private void respondWithPeers(final HttpServerExchange exchange) {
		respond(this.networkService.getPeers().toString(), exchange);
	}

	//Non-blocking
	private void respondWithLivePeers(final HttpServerExchange exchange) {
		respond(this.networkService.getLivePeers().toString(), exchange);
	}

	//Non-blocking
	private void respondWithNetwork(final HttpServerExchange exchange) {
		respond(this.networkService.getNetwork(), exchange);
	}

	//Non-blocking
	private void respondWaitingTaskCount(final HttpServerExchange exchange) {
		respond(jsonObject().put("count", atomsService.getWaitingCount()), exchange);
	}

	//Non-blocking
	private void respondWithSystem(final HttpServerExchange exchange) {
		respond(this.serialization.toJsonObject(this.localSystem, DsonOutput.Output.API), exchange);
	}

	//Non-blocking
	private void respondWithHighestQC(final HttpServerExchange exchange) {
		var highestQC = inMemorySystemInfo.getHighestQC();

		if (highestQC == null) {
			respond(jsonObject().put("error", "no qc"), exchange);
		} else {
			var highestQCJson = jsonObject()
				.put("epoch", highestQC.getEpoch())
				.put("view", highestQC.getView())
				.put("vertexId", highestQC.getProposed().getVertexId());

			respond(highestQCJson, exchange);
		}
	}

	//Non-blocking
	private void respondWithCommittedVertices(final HttpServerExchange exchange) {
		var array = jsonArray();

		inMemorySystemInfo.getCommittedVertices()
			.stream()
			.map(this::mapSingleVertex)
			.forEachOrdered(array::put);
		respond(array, exchange);
	}

	private void respondWithNode(HttpServerExchange exchange) {
		var json = jsonObject()
			.put("address", selfAddress);
		respond(json, exchange);
	}

	private JSONObject mapSingleVertex(final com.radixdlt.consensus.bft.VerifiedVertex v) {
		return jsonObject()
			.put("epoch", v.getParentHeader().getLedgerHeader().getEpoch())
			.put("view", v.getView().number())
			.put("hash", v.getId().toString());
	}

	private void respond(Object object, HttpServerExchange exchange) {
		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
		exchange.getResponseSender().send(object.toString());
	}

	private WebSocketProtocolHandshakeHandler createWebsocketHandler() {
		return Handlers.websocket(new RadixHttpWebsocketHandler(this, jsonRpcServer, peers, atomsService, serialization));
	}

	public void stop() {
		this.atomsService.stop();
		this.server.stop();
	}

	/**
	 * Close and remove a certain peer
	 *
	 * @param peer The peer to remove
	 */
	/*package*/ void closeAndRemovePeer(RadixJsonRpcPeer peer) {
		peers.remove(peer);
		peer.close();
	}

	@FunctionalInterface
	interface ThrowingConsumer<A> {
		void accept(final A arg1) throws PublicKeyException;
	}

	private void withJSONRequestBody(
		HttpServerExchange exchange,
		ThrowingConsumer<JSONObject> bodyHandler
	) throws IOException {
		exchange.startBlocking();

		try (var httpStreamReader = new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8)) {
			JSONObject values = new JSONObject(CharStreams.toString(httpStreamReader));

			bodyHandler.accept(values);
		} catch (JSONException e) {
			exchange.setStatusCode(StatusCodes.UNPROCESSABLE_ENTITY);
			return;
		} catch (PublicKeyException e) {
			exchange.setStatusCode(StatusCodes.BAD_REQUEST);
			return;
		}

		exchange.setStatusCode(StatusCodes.OK);
	}

	//Potentially blocking
	private void handleBftState(HttpServerExchange exchange) throws IOException {
		withJSONRequestBody(exchange, values -> {
			if (values.getBoolean("state")) {
				consensusRunner.start();
			} else {
				consensusRunner.stop();
			}
		});
	}

	//Potentially blocking
	private void handleMempoolFill(HttpServerExchange exchange) throws IOException {
		withJSONRequestBody(exchange, values -> {
			boolean enabled = values.getBoolean("enabled");
			mempoolFillerUpdateEventDispatcher.dispatch(MempoolFillerUpdate.create(enabled));
		});
	}

	private void handleValidatorRegistration(HttpServerExchange exchange) throws IOException {
		withJSONRequestBody(exchange, values -> {
			boolean enabled = values.getBoolean("enabled");
			validatorRegistrationEventDispatcher.dispatch(
				enabled ? ValidatorRegistration.register() : ValidatorRegistration.unregister()
			);
		});
	}

	//Non-blocking
	private void handleMessageFlood(HttpServerExchange exchange) throws IOException {
		withJSONRequestBody(exchange, values -> {
			var update = MessageFlooderUpdate.create();

			if (values.getBoolean("enabled")) {
				var data = values.getJSONObject("data");

				if (data.has("nodeKey")) {
					update = update.bftNode(createNodeByKey(data.getString("nodeKey")));
				}

				if (data.has("messagesPerSec")) {
					update = update.messagesPerSec(data.getInt("messagesPerSec"));
				}

				if (data.has("commandSize")) {
					update = update.commandSize(data.getInt("commandSize"));
				}
			}

			this.messageFloodUpdateEventDispatcher.dispatch(update);
		});
	}

	private BFTNode createNodeByKey(final String nodeKeyBase58) throws PublicKeyException {
		return BFTNode.create(ECPublicKey.fromBytes(fromBase58(nodeKeyBase58)));
	}

	/**
	 * Get a parameter from either path or query parameters from an http exchange.
	 * Note that path parameters are prioritised over query parameters in the event of a conflict.
	 *
	 * @param exchange The exchange to get the parameter from
	 * @param name The name of the parameter
	 *
	 * @return The parameter with the given name from the path or query parameters, or empty if it doesn't exist
	 */
	private static Optional<String> getParameter(HttpServerExchange exchange, String name) {
		// our routing handler puts path params into query params by default so we don't need to include them manually
		return Optional.ofNullable(exchange.getQueryParameters().get(name)).map(Deque::getFirst);
	}

	private class RpcPostHandler implements HttpHandler {
		@Override
		public void handleRequest(HttpServerExchange exchange) {
			// we need to be in another thread to do blocking io work, which is needed to extract the entire message body
			if (exchange.isInIoThread()) {
				exchange.dispatch(this);
				return;
			}

			CompletableFuture
				.supplyAsync(() -> jsonRpcServer.handleJsonRpc(exchange))
				.whenComplete((response, exception) -> {
					if (exception == null) {
						exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
						exchange.getResponseSender().send(response);
					} else {
						exchange.setStatusCode(400);
						exchange.getResponseSender().send("Invalid request: " + exception.getMessage());
					}
				});
		}
	}
}
