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

package com.radixdlt.api;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.io.CharStreams;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;

public final class RestUtils {
	public static final String CONTENT_TYPE_JSON = "application/json";

	public static final HttpString METHOD_HEADER = HttpString.tryFromString("X-Radixdlt-Method");
	public static final HttpString CORRELATION_HEADER = HttpString.tryFromString("X-Radixdlt-Correlation-Id");
	private static final long DEFAULT_MAX_REQUEST_SIZE = 1024L * 1024L;

	private RestUtils() {
		throw new IllegalStateException("Can't construct");
	}

	public static void withBody(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		if (exchange.isInIoThread()) {
			exchange.dispatch(() -> handleBody(exchange, bodyHandler));
		} else {
			handleBody(exchange, bodyHandler);
		}
	}

	public static void withBodyAsync(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		if (exchange.isInIoThread()) {
			exchange.dispatch(() -> handleAsync(exchange, bodyHandler));
		} else {
			try {
				handleBody(exchange, bodyHandler);
				sendStatusResponse(exchange, null);
			} catch (Exception e) {
				sendStatusResponse(exchange, e);
			}
		}
	}

	public static void respond(HttpServerExchange exchange, Object object) {
		respondWithCode(exchange, StatusCodes.OK, object.toString());
	}

	private static void handleAsync(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		CompletableFuture
			.runAsync(() -> handleBody(exchange, bodyHandler))
			.whenComplete((__, err) -> sendStatusResponse(exchange, err));
	}

	private static void sendStatusResponse(HttpServerExchange exchange, Throwable err) {
		if (err == null) {
			if (!exchange.isResponseStarted()) {
				respond(exchange, jsonObject());
			}
			return;
		}

		if (!(err instanceof RuntimeException)) {
			sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Unable to handle request", err);
			return;
		}

		var exception = err.getCause();

		if (exception instanceof JSONException) {
			sendError(exchange, StatusCodes.UNPROCESSABLE_ENTITY, "Error while parsing request JSON", exception);
		} else if (exception instanceof PublicKeyException) {
			sendError(exchange, StatusCodes.BAD_REQUEST, "Invalid public key", exception);
		} else {
			sendError(exchange, StatusCodes.INTERNAL_SERVER_ERROR, "Unable to handle request", exception);
		}
	}

	private static void handleBody(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		copyHeader(exchange, METHOD_HEADER);
		copyHeader(exchange, CORRELATION_HEADER);

		var body = readBody(exchange, DEFAULT_MAX_REQUEST_SIZE);

		try {
			bodyHandler.accept(new JSONObject(body));
		} catch (Exception t) {
			throw new RuntimeException(t);
		}
	}

	private static String readBody(HttpServerExchange exchange, long maxRequestSize) {
		exchange.setMaxEntitySize(maxRequestSize);
		exchange.startBlocking();

		try (var httpStreamReader = new InputStreamReader(exchange.getInputStream(), StandardCharsets.UTF_8)) {
			var source = CharStreams.toString(httpStreamReader);

			if (source.length() > maxRequestSize) {
				throw new JSONException("Request too long");
			}

			return source;
		} catch (Exception t) {
			throw new RuntimeException(t);
		}
	}

	private static void sendError(HttpServerExchange exchange, int statusCode, String message, Throwable error) {
		respondWithCode(exchange, statusCode, message + " (" + error.getMessage() + ")");
	}

	private static void respondWithCode(HttpServerExchange exchange, int statusCode, String data) {
		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
		exchange.setStatusCode(statusCode);
		exchange.getResponseSender().send(data);
	}

	private static void copyHeader(HttpServerExchange exchange, HttpString headerName) {
		var inputHeaders = exchange.getRequestHeaders();
		var outputHeaders = exchange.getResponseHeaders();

		Optional.ofNullable(inputHeaders.getFirst(headerName))
			.ifPresent(header -> outputHeaders.add(headerName, header));
	}
}
