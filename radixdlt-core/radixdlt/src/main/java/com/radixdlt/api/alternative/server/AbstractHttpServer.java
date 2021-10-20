package com.radixdlt.api.alternative.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.radixdlt.ModuleRunner;
import com.radixdlt.api.routing.Route;
import com.radixdlt.api.serialization.ApiSerializer;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.utils.functional.Result;
import com.stijndewitt.undertow.cors.AllowAll;
import com.stijndewitt.undertow.cors.Filter;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.RequestLimitingHandler;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.StatusCodes;

import static com.radixdlt.api.util.RestUtils.CONTENT_TYPE_JSON;
import static com.radixdlt.api.util.RestUtils.CORRELATION_HEADER;
import static com.radixdlt.api.util.RestUtils.METHOD_HEADER;
import static com.radixdlt.api.util.RestUtils.readBody;

import static java.util.logging.Logger.getLogger;

public class AbstractHttpServer implements ModuleRunner {
	private static final Logger log = LogManager.getLogger();
	private static final String DEFAULT_BIND_ADDRESS = "0.0.0.0";
	private static final int MAXIMUM_CONCURRENT_REQUESTS = Runtime.getRuntime().availableProcessors() * 8; // same as workerThreads = ioThreads * 8
	private static final int QUEUE_SIZE = 2000;
	private static final long MAX_REQUEST_SIZE = 1024L * 1024L;

	private final String name;
	private final RouteMapping mapping;
	private final ApiSerializer serializer;
	private final int port;
	private final String bindAddress;

	private Undertow server;

	protected AbstractHttpServer(
		RuntimeProperties properties,
		String name,
		int defaultPort,
		RouteMapping mapping,
		ApiSerializer serializer
	) {
		this.name = name.toLowerCase(Locale.US);
		this.mapping = mapping;
		this.serializer = serializer;
		this.port = properties.get("api." + name + ".port", defaultPort);
		this.bindAddress = properties.get("api." + name + ".bind.address", DEFAULT_BIND_ADDRESS);
	}

	@Override
	public void start() {
		final var handler = new RequestLimitingHandler(MAXIMUM_CONCURRENT_REQUESTS, QUEUE_SIZE, configureRoutes());

		server = Undertow.builder()
			.addHttpListener(port, bindAddress)
			.setHandler(handler)
			.build();
		server.start();

		log.info("Starting {} HTTP Server at {}:{}", name.toUpperCase(Locale.US), bindAddress, port);
	}

	@Override
	public void stop() {
		server.stop();
	}

	private void configureRouting(RoutingHandler routingHandler) {
		mapping.forEach((route, handler) -> addRoute(route, handler, routingHandler));
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void addRoute(Route<?, ?> route, Function handler, RoutingHandler routingHandler) {
		HttpHandler wrappedHandler = exchange -> handleRequest((Function<Object, Result<Object>>) handler, route, exchange);
		var path = route.path();

		routingHandler.post(path, wrappedHandler);
		routingHandler.post(path.substring(0, path.length() - 1), wrappedHandler);
	}

	private void handleRequest(Function<Object, Result<Object>> handler, Route<?, ?> route, HttpServerExchange exchange) {
		if (exchange.isInIoThread()) {
			exchange.dispatch(() -> handleBody(handler, route, exchange));
		} else {
			handleBody(handler, route, exchange);
		}
	}

	private void handleBody(Function<Object, Result<Object>> handler, Route<?,?> route, HttpServerExchange exchange) {
		serializer.deserialize(readBody(exchange, MAX_REQUEST_SIZE), route.descriptor().requestType())
			.flatMap(handler)
			.flatMap(serializer::serialize)
			.fold(
				failure -> sendResponse(exchange, calculateStatusCode(failure.code()), failure.message()),
				success -> sendResponse(exchange, StatusCodes.OK, success)
			);
	}

	private HttpHandler configureRoutes() {
		var handler = Handlers.routing(true); // add path params to query params with this flag

		configureRouting(handler);

		handler.setFallbackHandler(AbstractHttpServer::fallbackHandler);
		handler.setInvalidMethodHandler(AbstractHttpServer::invalidMethodHandler);

		return wrapWithCorsFilter(handler);
	}

	private static Filter wrapWithCorsFilter(final RoutingHandler handler) {
		var filter = new Filter(handler);

		// Disable INFO logging for CORS filter, as it's a bit distracting
		getLogger(filter.getClass().getName()).setLevel(Level.WARNING);
		filter.setPolicyClass(AllowAll.class.getName());
		filter.setUrlPattern("^.*$");

		return filter;
	}

	private static int calculateStatusCode(int code) {
		//TODO: add real translation of status codes
		return code;
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

	private static Void sendResponse(HttpServerExchange exchange, int statusCode, String data) {
		copyHeader(exchange, METHOD_HEADER);
		copyHeader(exchange, CORRELATION_HEADER);

		exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, CONTENT_TYPE_JSON);
		exchange.setStatusCode(statusCode);
		exchange.getResponseSender().send(data);

		return null;
	}

	private static void copyHeader(HttpServerExchange exchange, HttpString headerName) {
		var inputHeaders = exchange.getRequestHeaders();
		var outputHeaders = exchange.getResponseHeaders();

		Optional.ofNullable(inputHeaders.getFirst(headerName))
			.ifPresent(header -> outputHeaders.add(headerName, header));
	}
}
