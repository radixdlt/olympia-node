/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.io.CharStreams;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

public class RestUtils {
	public static final String CONTENT_TYPE_JSON = "application/json";

	private RestUtils() {}

	public static void withBodyAsync(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		if (exchange.isInIoThread()) {
			exchange.dispatch(() -> handleAsync(exchange, bodyHandler));
		}
	}

	private static CompletableFuture<Void> handleAsync(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		return CompletableFuture
			.runAsync(() -> handleBody(exchange, bodyHandler))
			.whenComplete((__, err) -> sendResponse(exchange, err));
	}

	public static void sendResponse(final HttpServerExchange exchange, final Throwable err) {
		if (err == null) {
			exchange.setStatusCode(StatusCodes.OK);
			return;
		}

		if (!(err instanceof RuntimeException)) {
			exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
			exchange.getResponseSender().send("Unable to handle request: " + err.getMessage());
			return;
		}

		var exception = err.getCause();

		if (exception instanceof JSONException) {
			exchange.setStatusCode(StatusCodes.UNPROCESSABLE_ENTITY);
			exchange.getResponseSender().send("Error while parsing request JSON");
		} else if (exception instanceof PublicKeyException) {
			exchange.setStatusCode(StatusCodes.BAD_REQUEST);
			exchange.getResponseSender().send("Invalid public key");
		} else if (exception instanceof IOException) {
			exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);
			exchange.getResponseSender().send("Unable to handle request:" + exception.getMessage());
		}
	}

	public static void handleBody(final HttpServerExchange exchange, final ThrowingConsumer<JSONObject> bodyHandler) {
		var blockingExchange = exchange.startBlocking();

		try (var httpStreamReader = new InputStreamReader(blockingExchange.getInputStream(), StandardCharsets.UTF_8)) {
			bodyHandler.accept(new JSONObject(CharStreams.toString(httpStreamReader)));
		} catch (Exception t) {
			throw new RuntimeException(t);
		}
	}

	public static void respond(HttpServerExchange exchange, Object object) {
		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
		exchange.getResponseSender().send(object.toString());
	}

	public static <T> void respondAsync(HttpServerExchange exchange, Supplier<T> objectSupplier) {
		if (exchange.isInIoThread()) {
			exchange.dispatch(() -> {
				CompletableFuture
					.supplyAsync(objectSupplier)
					.whenComplete((response, exception) -> {
						if (exception == null) {
							respond(exchange, response);
						} else {
							exchange.setStatusCode(StatusCodes.BAD_REQUEST);
							exchange.getResponseSender().send("Unable to handle request: " + exception.getMessage());
						}
					});
			});
		}
	}

	public static Optional<String> getParameter(HttpServerExchange exchange, String name) {
		// our routing handler puts path params into query params by default so we don't need to include them manually
		return Optional.ofNullable(exchange.getQueryParameters().get(name)).map(Deque::getFirst);
	}
}
