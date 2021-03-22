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
import org.radix.api.jsonrpc.handler.AtomHandler;
import org.radix.api.jsonrpc.handler.HighLevelApiHandler;
import org.radix.api.jsonrpc.handler.LedgerHandler;
import org.radix.api.jsonrpc.handler.NetworkHandler;
import org.radix.api.jsonrpc.handler.SystemHandler;

import com.google.common.io.CharStreams;
import com.google.inject.Inject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.undertow.server.HttpServerExchange;

import static org.radix.api.jsonrpc.JsonRpcUtil.INVALID_PARAMS;
import static org.radix.api.jsonrpc.JsonRpcUtil.PARSE_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.REQUEST_TOO_LONG;
import static org.radix.api.jsonrpc.JsonRpcUtil.SERVER_ERROR;
import static org.radix.api.jsonrpc.JsonRpcUtil.errorResponse;
import static org.radix.api.jsonrpc.JsonRpcUtil.jsonObject;
import static org.radix.api.jsonrpc.JsonRpcUtil.methodNotFoundResponse;

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
	 * Store to query atoms from
	 */
	private final Map<String, Function<JSONObject, JSONObject>> handlers = new HashMap<>();
	private final SystemHandler systemHandler;
	private final NetworkHandler networkHandler;
	private final AtomHandler atomHandler;
	private final LedgerHandler ledgerHandler;
	private final HighLevelApiHandler highLevelApiHandler;

	@Inject
	public RadixJsonRpcServer(
		SystemHandler systemHandler,
		NetworkHandler networkHandler,
		AtomHandler atomHandler,
		LedgerHandler ledgerHandler,
		HighLevelApiHandler highLevelApiHandler
	) {
		this(systemHandler, networkHandler, atomHandler, ledgerHandler, highLevelApiHandler, DEFAULT_MAX_REQUEST_SIZE);
	}

	public RadixJsonRpcServer(
		SystemHandler systemHandler,
		NetworkHandler networkHandler,
		AtomHandler atomHandler,
		LedgerHandler ledgerHandler,
		HighLevelApiHandler highLevelApiHandler,
		long maxRequestSizeBytes
	) {
		this.systemHandler = systemHandler;
		this.networkHandler = networkHandler;
		this.atomHandler = atomHandler;
		this.ledgerHandler = ledgerHandler;
		this.highLevelApiHandler = highLevelApiHandler;
		this.maxRequestSizeBytes = maxRequestSizeBytes;

		fillHandlers();
	}

	private void fillHandlers() {
		//BFT
		handlers.put("BFT.start", systemHandler::handleBftStart);
		handlers.put("BFT.stop", systemHandler::handleBftStop);

		//General info
		handlers.put("Universe.getUniverse", systemHandler::handleGetUniverse);
		handlers.put("Network.getInfo", systemHandler::handleGetLocalSystem);
		handlers.put("Ping", systemHandler::handlePing);

		//Network info
		handlers.put("Network.getLivePeers", networkHandler::handleGetLivePeers);
		handlers.put("Network.getPeers", networkHandler::handleGetPeers);

		//Atom submission/retrieval
		//TODO: check and fix method naming?
		handlers.put("Atoms.submitAtom", atomHandler::handleSubmitAtom);
		handlers.put("Ledger.getAtom", atomHandler::handleGetAtom);

		//Ledger
		//TODO: check and fix method naming?
		handlers.put("Atoms.getAtomStatus", ledgerHandler::handleGetAtomStatus);

		//High level API's
		handlers.put("radix.universeMagic", highLevelApiHandler::handleUniverseMagic);
		handlers.put("radix.nativeToken", highLevelApiHandler::handleNativeToken);
		handlers.put("radix.tokenBalances", highLevelApiHandler::handleTokenBalances);
		handlers.put("radix.executedTransactions", highLevelApiHandler::handleExecutedTransactions);
		handlers.put("radix.transactionStatus", highLevelApiHandler::handleTransactionStatus);
	}

	/**
	 * Extract a JSON RPC API request from an HttpServerExchange, handle it as usual and return the response
	 *
	 * @param exchange The JSON RPC API request
	 *
	 * @return The response
	 */
	public String handleJsonRpc(HttpServerExchange exchange) {
		try {
			// Switch to blocking since we need to retrieve whole request body
			exchange.setMaxEntitySize(maxRequestSizeBytes);
			exchange.startBlocking();

			var requestBody = CharStreams.toString(new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8));
			return handleRpc(requestBody);
		} catch (IOException e) {
			throw new IllegalStateException("RPC failed", e);
		}
	}

	/**
	 * Handle the string JSON-RPC request with size checks, return appropriate error if size exceeds the limit.
	 *
	 * @param requestString The string JSON-RPC request
	 *
	 * @return The response to the request, could be a JSON-RPC error
	 */
	String handleRpc(String requestString) {
		int length = requestString.getBytes(StandardCharsets.UTF_8).length;

		if (length > maxRequestSizeBytes) {
			return errorResponse(REQUEST_TOO_LONG, "request too big: " + length + " > " + maxRequestSizeBytes).toString();
		}

		return jsonObject(requestString)
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
				.orElseGet(() -> methodNotFoundResponse(request.get("id")));

		} catch (Exception e) {
			var id = request.get("id");
			if (request.has("params") && request.get("params") instanceof JSONObject) {
				return errorResponse(id, SERVER_ERROR, e.getMessage(), request.getJSONObject("params"));
			} else {
				return errorResponse(id, SERVER_ERROR, e.getMessage());
			}
		}
	}
}
