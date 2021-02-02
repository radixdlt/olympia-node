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

import com.google.inject.Inject;
import com.radixdlt.ModuleRunner;
import com.radixdlt.chaos.MessageFloodUpdate;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.bft.VerifiedVertex;
import com.radixdlt.crypto.exception.PublicKeyException;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.middleware2.store.CommandToBinaryConverter;
import com.radixdlt.statecomputer.AtomCommittedToLedger;
import com.radixdlt.statecomputer.ClientAtomToBinaryConverter;
import com.radixdlt.systeminfo.InMemorySystemInfo;
import com.google.common.io.CharStreams;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.DsonOutput;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.universe.Universe;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import io.reactivex.rxjava3.core.Observable;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.core.WebSocketChannel;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.radix.api.jsonrpc.RadixJsonRpcPeer;
import org.radix.api.jsonrpc.RadixJsonRpcServer;
import org.radix.api.services.AtomsService;
import org.radix.api.services.InternalService;
import org.radix.api.services.NetworkService;
import org.radix.time.Time;
import org.radix.universe.system.LocalSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Document me!
 */
public final class RadixHttpServer {
	public static final int DEFAULT_PORT = 8080;
	public static final String CONTENT_TYPE_JSON = "application/json";

	private static final Logger logger = LogManager.getLogger();

	private final ConcurrentHashMap<RadixJsonRpcPeer, WebSocketChannel> peers;
	private final ModuleRunner consensusRunner;
	private final AtomsService atomsService;
	private final RadixJsonRpcServer jsonRpcServer;
	private final InternalService internalService;
	private final NetworkService networkService;
	private final Universe universe;
	private final JSONObject apiSerializedUniverse;
	private final LocalSystem localSystem;
	private final Serialization serialization;
	private final InMemorySystemInfo inMemorySystemInfo;
	private final int port;
	private Undertow server;

	private final EventDispatcher<MessageFloodUpdate> messageFloodSetEventDispatcher;

	@Inject
	public RadixHttpServer(
		InMemorySystemInfo inMemorySystemInfo,
		Observable<MempoolAddFailure> mempoolAddFailures,
		Observable<AtomCommittedToLedger> ledgerCommitted,
		Map<String, ModuleRunner> moduleRunners,
		LedgerEntryStore store,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		EventDispatcher<MessageFloodUpdate> messageFloodSetEventDispatcher,
		CommandToBinaryConverter commandToBinaryConverter,
		ClientAtomToBinaryConverter clientAtomToBinaryConverter,
		Universe universe,
		Serialization serialization,
		RuntimeProperties properties,
		LocalSystem localSystem,
		AddressBook addressBook,
		Hasher hasher
	) {
		this.messageFloodSetEventDispatcher = Objects.requireNonNull(messageFloodSetEventDispatcher);
		this.inMemorySystemInfo = Objects.requireNonNull(inMemorySystemInfo);
		this.consensusRunner = Objects.requireNonNull(moduleRunners.get("consensus"));
		this.universe = Objects.requireNonNull(universe);
		this.serialization = Objects.requireNonNull(serialization);
		this.apiSerializedUniverse = serialization.toJsonObject(this.universe, DsonOutput.Output.API);
		this.localSystem = Objects.requireNonNull(localSystem);
		this.peers = new ConcurrentHashMap<>();
		this.atomsService = new AtomsService(
			mempoolAddFailures,
			ledgerCommitted,
			store,
			mempoolAddEventDispatcher,
			commandToBinaryConverter,
			clientAtomToBinaryConverter,
			hasher
		);
		this.jsonRpcServer = new RadixJsonRpcServer(
			consensusRunner,
			serialization,
			store,
			atomsService,
			localSystem,
			addressBook,
			universe
		);
		this.internalService = new InternalService(mempoolAddEventDispatcher, properties, universe, hasher);
		this.networkService = new NetworkService(serialization, localSystem, addressBook, hasher);
		this.port = properties.get("cp.port", DEFAULT_PORT);
	}

    /**
     * Get the set of currently connected peers
     *
     * @return The currently connected peers
     */
    public Set<RadixJsonRpcPeer> getPeers() {
        return Collections.unmodifiableSet(peers.keySet());
    }

	public void start() {
		this.atomsService.start();

		RoutingHandler handler = Handlers.routing(true); // add path params to query params with this flag

		// add all REST routes
		addRestRoutesTo(handler);

		// handle POST requests
		addPostRoutesTo(handler);

		// handle websocket requests (which also uses GET method)
		handler.add(
			Methods.GET,
			"/rpc",
			Handlers.websocket(new RadixHttpWebsocketHandler(this, jsonRpcServer, peers, atomsService, serialization))
		);

		// add appropriate error handlers for meaningful error messages (undertow is silent by default)
		handler.setFallbackHandler(exchange -> {
			exchange.setStatusCode(StatusCodes.NOT_FOUND);
			exchange.getResponseSender().send(
				"No matching path found for " + exchange.getRequestMethod() + " " + exchange.getRequestPath()
			);
		});
		handler.setInvalidMethodHandler(exchange -> {
			exchange.setStatusCode(StatusCodes.NOT_ACCEPTABLE);
			exchange.getResponseSender().send(
				"Invalid method, path exists for " + exchange.getRequestMethod() + " " + exchange.getRequestPath()
			);
		});

		// if we are in a development universe, add the dev only routes (e.g. for spamathons)
		if (this.universe.isDevelopment()) {
			addDevelopmentOnlyRoutesTo(handler);
		}
		if (this.universe.isDevelopment() || this.universe.isTest()) {
			addTestRoutesTo(handler);
		}

		Filter corsFilter = new Filter(handler);
		// Disable INFO logging for CORS filter, as it's a bit distracting
		java.util.logging.Logger.getLogger(corsFilter.getClass().getName()).setLevel(java.util.logging.Level.WARNING);
		corsFilter.setPolicyClass(AllowAll.class.getName());
		corsFilter.setUrlPattern("^.*$");
		server = Undertow.builder()
				.addHttpListener(port, "0.0.0.0")
				.setHandler(corsFilter)
				.build();
		server.start();
	}

	public void stop() {
		this.atomsService.stop();
		this.server.stop();
	}

	private void addDevelopmentOnlyRoutesTo(RoutingHandler handler) {
		addGetRoute("/api/internal/spamathon", exchange -> {
			String iterations = getParameter(exchange, "iterations").orElse(null);
			String batching = getParameter(exchange, "batching").orElse(null);
			String rate = getParameter(exchange, "rate").orElse(null);

			respond(internalService.spamathon(iterations, batching, rate), exchange);
		}, handler);
	}

	private void addTestRoutesTo(RoutingHandler handler) {
		addGetRoute("/api/vertices/committed", exchange -> {
			List<VerifiedVertex> vertices = inMemorySystemInfo.getCommittedVertices();
			JSONArray array = new JSONArray();
			vertices.stream()
				.map(v -> new JSONObject()
					.put("epoch", v.getParentHeader().getLedgerHeader().getEpoch())
					.put("view", v.getView().number())
					.put("hash", v.getId().toString())
				)
				.forEachOrdered(array::put);
			respond(array, exchange);
		}, handler);

		addGetRoute("/api/vertices/highestqc", exchange -> {
			QuorumCertificate highestQC = inMemorySystemInfo.getHighestQC();
			if (highestQC == null) {
				JSONObject errorJson = new JSONObject();
				errorJson.put("error", "no qc");
				respond(errorJson, exchange);
			} else {
				JSONObject highestQCJson = new JSONObject();
				highestQCJson.put("epoch", highestQC.getProposed().getLedgerHeader().getEpoch());
				highestQCJson.put("view", highestQC.getView());
				highestQCJson.put("vertexId", highestQC.getProposed().getVertexId());
				respond(highestQCJson, exchange);
			}
		}, handler);
	}

	private void addPostRoutesTo(RoutingHandler handler) {
		HttpHandler rpcPostHandler = new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) {
				// we need to be in another thread to do blocking io work, which is needed to extract the entire message body
				if (exchange.isInIoThread()) {
					exchange.dispatch(this);
					return;
				}

				exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
				try {
					exchange.getResponseSender().send(jsonRpcServer.handleChecked(exchange));
				} catch (IOException e) {
					exchange.setStatusCode(400);
					exchange.getResponseSender().send("Invalid request: " + e.getMessage());
				}
			}
		};
		handler.add(Methods.POST, "/rpc", rpcPostHandler);
		handler.add(Methods.POST, "/rpc/", rpcPostHandler); // handle both /rpc and /rpc/ for usability
	}

	private void addRestRoutesTo(RoutingHandler handler) {
		// TODO: organize routes in a nicer way
		// System routes
		addRestSystemRoutesTo(handler);

		// Network routes
		addRestNetworkRoutesTo(handler);

		addGetRoute("/api/universe", exchange
			-> respond(this.apiSerializedUniverse, exchange), handler);

		addGetRoute("/api/system/modules/api/tasks-waiting", exchange -> {
			JSONObject waiting = new JSONObject();
			waiting.put("count", atomsService.getWaitingCount());

			respond(waiting, exchange);
		}, handler);

		addGetRoute("/api/system/modules/api/websockets", exchange -> {
			JSONObject count = new JSONObject();
			count.put("count", getPeers().size());
			respond(count, exchange);
		}, handler);

		// delete method to disconnect all peers
		addRoute("/api/system/modules/api/websockets", Methods.DELETE_STRING, exchange -> {
			JSONObject result = this.disconnectAllPeers();
			respond(result, exchange);
		}, handler);

		addRoute("/api/chaos/message-flood", Methods.POST_STRING, this::handleMessageFlood, handler);

		addRoute("/api/bft/0", Methods.PUT_STRING, this::handleBftState, handler);

		// keep-alive
		addGetRoute("/api/ping", exchange -> {
			JSONObject obj = new JSONObject();
			obj.put("response", "pong");
			obj.put("timestamp", Time.currentTimestamp());
			respond(obj, exchange);
		}, handler);
	}

	private void addRestNetworkRoutesTo(RoutingHandler handler) {
		addGetRoute("/api/network", exchange
				-> respond(this.networkService.getNetwork(), exchange), handler);
		addGetRoute("/api/network/peers/live", exchange
				-> respond(this.networkService.getLivePeers().toString(), exchange), handler);
		addGetRoute("/api/network/peers", exchange
				-> respond(this.networkService.getPeers().toString(), exchange), handler);
		addGetRoute("/api/network/peers/{id}", exchange
				-> respond(this.networkService.getPeer(getParameter(exchange, "id").orElse(null)), exchange), handler);

	}

	private void addRestSystemRoutesTo(RoutingHandler handler) {
		addGetRoute("/api/system", exchange
				-> respond(this.serialization.toJsonObject(this.localSystem, DsonOutput.Output.API), exchange), handler);
	}

	// helper methods for responding to an exchange with various objects for readability
	private void respond(String object, HttpServerExchange exchange) {
		exchange.getResponseSender().send(object);
	}

	private void respond(JSONObject object, HttpServerExchange exchange) {
		exchange.getResponseSender().send(object.toString());
	}

	private void respond(JSONArray object, HttpServerExchange exchange) {
		exchange.getResponseSender().send(object.toString());
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

	/**
	 * Disconnect all currently connected peers
	 *
	 * @return Json object containing disconnect information
	 */
	public JSONObject disconnectAllPeers() {
		JSONObject result = new JSONObject();
		JSONArray closed = new JSONArray();
		result.put("closed", closed);
		HashMap<RadixJsonRpcPeer, WebSocketChannel> peersCopy = new HashMap<>(peers);

		peersCopy.forEach((peer, ws) -> {
			JSONObject closedPeer = new JSONObject();
			closedPeer.put("isOpen", ws.isOpen());
			closedPeer.put("closedReason", ws.getCloseReason());
			closedPeer.put("closedCode", ws.getCloseCode());
			closed.put(closedPeer);

			closeAndRemovePeer(peer);
			try {
				ws.close();
			} catch (IOException e) {
				logger.error("Error while closing web socket", e);
			}
		});

		result.put("closedCount", peersCopy.size());

		return result;
	}

	private void handleMessageFlood(HttpServerExchange exchange) throws IOException {
		exchange.startBlocking();
		try (InputStream httpStream = exchange.getInputStream();
			 InputStreamReader httpStreamReader = new InputStreamReader(httpStream, StandardCharsets.UTF_8)) {
			String requestBody = CharStreams.toString(httpStreamReader);
			JSONObject values = new JSONObject(requestBody);
			String nodeKey = values.getString("nodeKey");
			if (nodeKey != null) {
				BFTNode node = BFTNode.create(ECPublicKey.fromBase64(nodeKey));
				this.messageFloodSetEventDispatcher.dispatch(MessageFloodUpdate.create(node));
			} else {
				this.messageFloodSetEventDispatcher.dispatch(MessageFloodUpdate.disable());
			}

		} catch (PublicKeyException e) {
			exchange.setStatusCode(StatusCodes.BAD_REQUEST);
			return;
		}
		exchange.setStatusCode(StatusCodes.OK);
	}

	/**
	 * Handle PUT request for changing BFT state.
	 * <p>
	 * Put request takes two parameters:
	 * <ul>
	 *   <li><b>id</b> the ID of the BFT instance (must be {@code 0} for now)</li>
	 *   <li><b>state</b> {@code true} to enable the specified instance id, otherwise
	 *     the instance is disabled
	 * </ul>
	 *
	 * @param exchange The {@link HttpServerExchange} to use
	 * @throws IOException if an error occurs parsing form data
	 */
	private void handleBftState(HttpServerExchange exchange) throws IOException {
		exchange.startBlocking();
		try (InputStream httpStream = exchange.getInputStream();
			InputStreamReader httpStreamReader = new InputStreamReader(httpStream, StandardCharsets.UTF_8)) {
			String requestBody = CharStreams.toString(httpStreamReader);
			JSONObject values = new JSONObject(requestBody);
			if (values.getBoolean("state")) {
				consensusRunner.start();
			} else {
				consensusRunner.stop();
			}
		}
		exchange.setStatusCode(StatusCodes.OK);
	}

	/**
	 * Add a GET method route with JSON content a certain path and consumer to the given handler
	 *
	 * @param prefixPath       The prefix path
	 * @param responseFunction The consumer that processes incoming exchanges
	 * @param routingHandler   The routing handler to add the route to
	 */
	private static void addGetRoute(String prefixPath, ManagedHttpExchangeConsumer responseFunction, RoutingHandler routingHandler) {
		addRoute(prefixPath, Methods.GET_STRING, responseFunction, routingHandler);
	}

	/**
	 * Add a route with JSON content and a certain path, method, and consumer to the given handler
	 *
	 * @param prefixPath       The prefix path
	 * @param method           The HTTP method
	 * @param responseFunction The consumer that processes incoming exchanges
	 * @param routingHandler   The routing handler to add the route to
	 */
	private static void addRoute(String prefixPath, String method, ManagedHttpExchangeConsumer responseFunction, RoutingHandler routingHandler) {
		addRoute(prefixPath, method, CONTENT_TYPE_JSON, responseFunction::accept, routingHandler);
	}

	/**
	 * Add a route with a certain path, method, content type and consumer to the given handler
	 *
	 * @param prefixPath       The prefix path
	 * @param method           The HTTP method
	 * @param contentType      The MIME type
	 * @param responseFunction The consumer that processes incoming exchanges
	 * @param routingHandler   The routing handler to add the route to
	 */
	private static void addRoute(
		String prefixPath,
		String method,
		String contentType,
		ManagedHttpExchangeConsumer responseFunction,
		RoutingHandler routingHandler
	) {
		routingHandler.add(method, prefixPath, exchange -> {
			exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, contentType);
			responseFunction.accept(exchange);
		});
	}

	/**
	 * Get a parameter from either path or query parameters from an http exchange.
	 * Note that path parameters are prioritised over query parameters in the event of a conflict.
	 *
	 * @param exchange The exchange to get the parameter from
	 * @param name     The name of the parameter
	 * @return The parameter with the given name from the path or query parameters, or empty if it doesn't exist
	 */
	private static Optional<String> getParameter(HttpServerExchange exchange, String name) {
		// our routing handler puts path params into query params by default so we don't need to include them manually
		return Optional.ofNullable(exchange.getQueryParameters().get(name)).map(Deque::getFirst);
	}
}

