/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.faucet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.identifiers.EUID;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import java.security.SecureRandom;
import java.util.Deque;
import java.util.Optional;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;

/**
 * Token request source via API.
 */
final class APITokenRequestSource implements TokenRequestSource {
	private static final Logger logger = LogManager.getLogger();

	private final Undertow server;
	private final Subject<Pair<RadixAddress, EUID>> requests;
	private final SecureRandom random;
	private final int port;

	/**
	 * Creates a new {@code FaucetAPI} listening on the specified port.
	 *
	 * @param apiPort The port to listen on
	 *
	 * @return A newly created {@code FaucetAPI}
	 */
	static APITokenRequestSource create(int apiPort) {
		return new APITokenRequestSource(apiPort);
	}

	/**
	 * Returns an observable of requests.
	 *
	 * @return An observable of requests.
	 */
	@Override
	public Observable<Pair<RadixAddress, EUID>> requestSource() {
		return this.requests;
	}

	/**
	 * Returns port at which observable is listening.
	 *
	 * @return port number
	 */
	@Override
	public int port() {
		return port;
	}

	/**
	 * Stops the server.
	 */
	void stop() {
		this.server.stop();
	}

	private APITokenRequestSource(int port) {
		this.requests = BehaviorSubject.create();
		this.random = new SecureRandom();
		this.port = port;

		var handler = Handlers.routing(true); // add path params to query params with this flag

		handler.add(Methods.GET, "/api/v1/getTokens/{address}", this::getTokens);

		// add appropriate error handlers for meaningful error messages (undertow is silent by default)
		handler.setFallbackHandler(this::fallbackHandler);

		this.server = Undertow.builder()
			.addHttpListener(port, "0.0.0.0")
			.setHandler(handler)
			.build();

		this.server.start();
	}

	private void fallbackHandler(final HttpServerExchange exchange) {
		logger.debug(
			"No matching path found for {} {} from {}",
			exchange.getRequestMethod(),
			exchange.getRequestPath(),
			exchange.getSourceAddress()
		);

		exchange.setStatusCode(StatusCodes.NOT_FOUND);
		exchange.getResponseSender().send(
			"No matching path found for " + exchange.getRequestMethod() + " " + exchange.getRequestPath()
		);
	}

	private void getTokens(HttpServerExchange exchange) {
		parameter(exchange, "address")
			.map(RadixAddress::from)
			.ifPresentOrElse(
				address -> handleExistingAddress(exchange, address),
				() -> handleMissingAddress(exchange)
			);
	}

	private void handleExistingAddress(final HttpServerExchange exchange, final RadixAddress address) {
		var result = Pair.of(address, randomEuid());
		logger.debug(
			"Queuing request {}, address {} from {}",
			result.getSecond(),
			result.getFirst(),
			exchange.getSourceAddress()
		);
		this.requests.onNext(result);
		exchange.setStatusCode(StatusCodes.OK);
		exchange.getResponseSender().send(result.getSecond().toString());
	}

	private void handleMissingAddress(final HttpServerExchange exchange) {
		exchange.setStatusCode(StatusCodes.BAD_REQUEST);
		exchange.getResponseSender().send("Address must be specified");
		logger.debug("Missing address in request from {}", exchange.getSourceAddress());
	}

	private Optional<String> parameter(final HttpServerExchange exchange, final String key) {
		return Optional.ofNullable(exchange.getQueryParameters().get(key)).map(Deque::getFirst);
	}

	private EUID randomEuid() {
		byte[] value = new byte[EUID.BYTES];
		this.random.nextBytes(value);
		return new EUID(value);
	}
}
