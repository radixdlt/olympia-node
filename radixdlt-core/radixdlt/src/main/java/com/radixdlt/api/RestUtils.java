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

package com.radixdlt.api;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.io.CharStreams;
import com.radixdlt.crypto.exception.PublicKeyException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
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
			exchange.dispatch(() -> safeHandleBody(exchange, bodyHandler));
		} else {
			safeHandleBody(exchange, bodyHandler);
		}
	}

	public static void respond(HttpServerExchange exchange, Object object) {
		respondWithCode(exchange, StatusCodes.OK, object.toString());
	}

	public static String sanitizeBaseUrl(String baseUrl) {
		return !baseUrl.endsWith("/")
			   ? baseUrl
			   : baseUrl.substring(0, baseUrl.length() - 1);
	}

	private static void safeHandleBody(HttpServerExchange exchange, ThrowingConsumer<JSONObject> bodyHandler) {
		try {
			handleBody(exchange, bodyHandler);
			sendStatusResponse(exchange, null);
		} catch (Exception e) {
			sendStatusResponse(exchange, e);
		}
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
