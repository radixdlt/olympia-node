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
import org.json.JSONObject;

import com.google.inject.Inject;
import com.radixdlt.api.JsonRpcHandler;

import java.util.HashMap;
import java.util.Map;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import static com.radixdlt.api.JsonRpcUtil.invalidParamsError;
import static com.radixdlt.api.JsonRpcUtil.methodNotFound;
import static com.radixdlt.api.RestUtils.respond;
import static com.radixdlt.api.RestUtils.withBody;

import static java.util.Optional.ofNullable;

/**
 * Stateless Json Rpc 2.0 Server
 */
public final class JsonRpcServer implements HttpHandler {
	private static final Logger log = LogManager.getLogger();

	private final Map<String, JsonRpcHandler> handlers = new HashMap<>();

	@Inject
	public JsonRpcServer(Map<String, JsonRpcHandler> additionalHandlers) {
		fillHandlers(additionalHandlers);
	}

	@Override
	public void handleRequest(HttpServerExchange exchange) {
		withBody(exchange, request -> respond(exchange, handle(request)));
	}

	public JSONObject handle(JSONObject request) {
		log.debug("RPC: input {}", request);

		if (!request.has("id")) {
			return invalidParamsError(request, "The 'id' missing");
		}

		if (!request.has("method")) {
			return invalidParamsError(request, "The method must be specified");
		}

		return ofNullable(handlers.get(logValue("method", request.getString("method"))))
			.map(handler -> logValue("output", handler.execute(request)))
			.orElseGet(() -> methodNotFound(request));
	}

	private void fillHandlers(Map<String, JsonRpcHandler> additionalHandlers) {
		handlers.putAll(additionalHandlers);
		handlers.keySet().forEach(name -> log.trace("Registered JSON RPC method: {}", name));
	}

	private static <T> T logValue(String message, T value) {
		log.debug("RPC: {} {}", message, value);
		return value;
	}
}
