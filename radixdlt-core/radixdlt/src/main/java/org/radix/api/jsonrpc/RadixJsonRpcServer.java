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

package org.radix.api.jsonrpc;

import org.json.JSONObject;
import org.radix.api.services.AtomsService;
import org.radix.universe.system.LocalSystem;

import com.google.common.io.CharStreams;
import com.radixdlt.ModuleRunner;
import com.radixdlt.identifiers.AID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.addressbook.PeerWithSystem;
import com.radixdlt.serialization.DsonOutput.Output;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;
import com.radixdlt.store.LedgerSearchMode;
import com.radixdlt.store.StoreIndex;
import com.radixdlt.universe.Universe;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.undertow.server.HttpServerExchange;

import static org.radix.api.jsonrpc.JsonRpcUtil.PARSE_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.REQUEST_TOO_LONG;
import static org.radix.api.jsonrpc.JsonRpcUtil.SERVER_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.INVALID_PARAMS;
import static org.radix.api.jsonrpc.JsonRpcUtil.commonFields;
import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.listToArray;

import static com.radixdlt.middleware2.store.EngineAtomIndices.IndexType;

/**
 * Stateless Json Rpc 2.0 Server
 */
public final class RadixJsonRpcServer {
	private static final long DEFAULT_MAX_REQUEST_SIZE = 1024L * 1024L;

	/**
	 * Maximum request size in bytes
	 */
	private final long maxRequestSizeBytes;

	/**
	 * Service to submit atoms through
	 */
	private final AtomsService atomsService;

	/**
	 * Store to query atoms from
	 */
	private final LedgerEntryStore ledger;

	private final Serialization serialization;
	private final LocalSystem localSystem;
	private final AddressBook addressBook;
	private final Universe universe;

	private final ModuleRunner consensusRunner;
	private final PeerWithSystem localPeer;

	private final Map<String, Function<JSONObject, JSONObject>> handlers = new HashMap<>();

	public RadixJsonRpcServer(
		ModuleRunner consensusRunner,
		Serialization serialization,
		LedgerEntryStore ledger,
		AtomsService atomsService,
		LocalSystem localSystem,
		AddressBook addressBook,
		Universe universe
	) {
		this(consensusRunner, serialization, ledger, atomsService, localSystem, addressBook, universe, DEFAULT_MAX_REQUEST_SIZE);
	}

	public RadixJsonRpcServer(
		ModuleRunner consensusRunner,
		Serialization serialization,
		LedgerEntryStore ledger,
		AtomsService atomsService,
		LocalSystem localSystem,
		AddressBook addressBook,
		Universe universe,
		long maxRequestSizeBytes
	) {
		this.consensusRunner = Objects.requireNonNull(consensusRunner);
		this.serialization = Objects.requireNonNull(serialization);
		this.ledger = Objects.requireNonNull(ledger);
		this.atomsService = Objects.requireNonNull(atomsService);
		this.localSystem = Objects.requireNonNull(localSystem);
		this.addressBook = Objects.requireNonNull(addressBook);
		this.universe = Objects.requireNonNull(universe);
		this.maxRequestSizeBytes = maxRequestSizeBytes;

		this.localPeer = new PeerWithSystem(this.localSystem);

		fillHandlers();
	}

	private void fillHandlers() {
		handlers.put("BFT.start", this::handleBftStart);
		handlers.put("BFT.stop", this::handleBftStop);
		handlers.put("Universe.getUniverse", this::handleGetUniverse);
		handlers.put("Network.getLivePeers", this::handleGetLivePeers);
		handlers.put("Network.getPeers", this::handleGetPeers);
		handlers.put("Network.getInfo", request -> fillPlainResponse(request, localSystem));
		handlers.put("Ping", this::handlePing);
		handlers.put("Atoms.submitAtom", this::handleSubmitAtom);
		handlers.put("Atoms.getAtomStatus", this::handleGetAtomStatus);
		handlers.put("Ledger.getAtom", this::handleGetAtom);
		handlers.put("Ledger.getAtoms", this::handleGetAtoms);
	}

	/**
	 * Extract a JSON RPC API request from an HttpServerExchange, handle it as usual and return the response
	 *
	 * @param exchange The JSON RPC API request
	 *
	 * @return The response
	 */
	public String handleJsonRpc(HttpServerExchange exchange) throws IOException {
		// Switch to blocking since we need to retrieve whole request body
		exchange.setMaxEntitySize(maxRequestSizeBytes).startBlocking();

		var requestBody = CharStreams.toString(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8));

		return this.handleRpc(requestBody);
	}

	/**
	 * Handle the string JSON-RPC request with size checks, return appropriate error if oversized
	 *
	 * @param requestString The string JSON-RPC request
	 *
	 * @return The response to the request, could be a JSON-RPC error
	 */
	String handleRpc(String requestString) {
		int length = requestString.getBytes(StandardCharsets.UTF_8).length;

		return length > maxRequestSizeBytes
			   ? errorResponse(REQUEST_TOO_LONG, "request too big: " + length + " > " + maxRequestSizeBytes).toString()
			   : jsonObject(requestString)
				   .map(this::handle)
				   .map(Object::toString)
				   .orElseGet(() -> errorResponse(PARSE_ERROR, "unable to parse input").toString());
	}

	private JSONObject handle(JSONObject request) {
		if (!request.has("id")) {
			return errorResponse(INVALID_PARAMS, "id missing");
		}

		if (!request.has("method")) {
			return errorResponse(INVALID_PARAMS, "method missing");
		}

		try {
			return Optional.ofNullable(handlers.get(request.getString("method")))
				.map(handler -> handler.apply(request))
				.orElseGet(() -> JsonRpcUtil.methodNotFoundResponse(request.get("id")));

		} catch (Exception e) {
			var id = request.get("id");
			if (request.has("params") && request.get("params") instanceof JSONObject) {
				return errorResponse(id, SERVER_ERROR, e.getMessage(), request.getJSONObject("params"));
			} else {
				return errorResponse(id, SERVER_ERROR, e.getMessage());
			}
		}
	}

	private JSONObject ifParameterPresent(
		final JSONObject request,
		final JSONObject params,
		final String name,
		final Function<JSONObject, JSONObject> fn
	) {
		if (!params.has(name)) {
			return errorResponse(request.get("id"), SERVER_ERROR, "Field '" + name + "' not present in params");
		} else {
			return fn.apply(params);
		}
	}

	private JSONObject ifParametersPresent(final JSONObject request, final Function<JSONObject, JSONObject> fn) {
		if (!request.has("params")) {
			return errorResponse(request.get("id"), SERVER_ERROR, "params field is required");
		}

		final Object paramsObject = request.get("params");

		if (!(paramsObject instanceof JSONObject)) {
			return errorResponse(request.get("id"), SERVER_ERROR, "params field must be a JSON object");
		}

		return fn.apply((JSONObject) paramsObject);
	}

	private JSONObject fillListResponse(final JSONObject request, final List<?> result) {
		return commonFields(request.get("id")).put("result", listToArray(serialization, result));
	}

	private JSONObject fillPlainResponse(final JSONObject request, final Object result) {
		var response = commonFields(request.get("id"));

		if (result instanceof List) {
			response.put("result", listToArray(serialization, (List<?>) result));
		} else {
			response.put("result", serialization.toJsonObject(result, Output.API));
		}

		return response;
	}

	private Stream<PeerWithSystem> selfAndOthers(Stream<PeerWithSystem> others) {
		return Stream.concat(Stream.of(this.localPeer), others).distinct();
	}

	//------------------------------------------------------------------------------------------
	// Handlers
	//------------------------------------------------------------------------------------------
	private JSONObject handleBftStart(JSONObject request) {
		consensusRunner.start();
		return fillPlainResponse(request, jsonObject().put("response", "success"));
	}

	private JSONObject handleBftStop(JSONObject request) {
		consensusRunner.stop();
		return fillPlainResponse(request, jsonObject().put("response", "success"));
	}

	private JSONObject handleGetUniverse(JSONObject request) {
		return fillPlainResponse(request, this.universe);
	}

	private JSONObject handleGetLivePeers(JSONObject request) {
		return fillListResponse(request, selfAndOthers(this.addressBook.recentPeers()).collect(Collectors.toList()));
	}

	private JSONObject handleGetPeers(JSONObject request) {
		return fillListResponse(request, selfAndOthers(this.addressBook.peers()).collect(Collectors.toList()));
	}

	private JSONObject handlePing(JSONObject request) {
		return fillPlainResponse(request, jsonObject()
			.put("response", "pong")
			.put("timestamp", System.currentTimeMillis()));
	}

	private JSONObject handleSubmitAtom(JSONObject request) {
		return ifParametersPresent(request, jsonAtom ->
			fillPlainResponse(request, jsonObject()
				.put("status", AtomStatus.PENDING_CM_VERIFICATION)
				.put("aid", atomsService.submitAtom(jsonAtom))
				.put("timestamp", System.currentTimeMillis())));
	}

	private JSONObject handleGetAtomStatus(JSONObject request) {
		return ifParametersPresent(request, paramsObject ->
			ifParameterPresent(request, paramsObject, "aid", params -> {
				var aid = AID.from(params.getString("aid"));
				var atomStatus = ledger.contains(aid) ? AtomStatus.STORED : AtomStatus.DOES_NOT_EXIST;

				return fillPlainResponse(request, jsonObject().put("status", atomStatus.toString()));
			}));
	}

	private JSONObject handleGetAtom(JSONObject request) {
		return ifParametersPresent(request, paramsObject ->
			ifParameterPresent(request, paramsObject, "aid", params ->
				AID.fromString(params.getString("aid"))
					.flatMap(atomsService::getAtomsByAtomId)
					.orElseGet(() -> errorResponse(
						request.get("id"),
						INVALID_PARAMS,
						"Atom with AID '" + params.getString("aid") + "' not found"
					))));
	}

	private JSONObject handleGetAtoms(JSONObject request) {
		return ifParametersPresent(request, paramsObject ->
			ifParameterPresent(request, paramsObject, "address", params -> {
				final var addressString = params.getString("address");
				final var address = RadixAddress.from(addressString);

				var index = new StoreIndex(IndexType.DESTINATION.getValue(), address.euid().toByteArray());
				var collectedAids = new ArrayList<>();
				var cursor = ledger.search(StoreIndex.LedgerIndexType.DUPLICATE, index, LedgerSearchMode.EXACT);

				while (cursor != null) {
					collectedAids.add(cursor.get());
					cursor = cursor.next();
				}
				return fillListResponse(request, collectedAids);
			}));
	}
}
