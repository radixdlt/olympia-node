package org.radix.api.http;

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;
import org.radix.logging.Logger;
import org.radix.logging.Logging;

/**
 * A shortcut interface for creating lambda methods that handle {@link HttpServerExchange}s in a safe way
 */
@FunctionalInterface
interface ManagedHttpExchangeConsumer {
    Logger logger = Logging.getLogger("api");

    /**
     * Process a given HTTP exchange safely, return an error if internal processing fails
     *
     * @param exchange The exchange
     */
    default void accept(HttpServerExchange exchange) {
        try {
            acceptInternal(exchange);
        } catch (Throwable e) {
            String errorMessage = "Error while serving request " + exchange.getRequestPath() + ": " + e;

            logger.error(errorMessage, e);
            exchange.setStatusCode(StatusCodes.INTERNAL_SERVER_ERROR);

            exchange.getResponseSender().send(errorMessage);
        }
    }

    /**
     * Process a given HTTP exchange without error handling
     * Note: This method is used as a shortcut for creating lambda exchange consumers that require exception handling.
     *
     * @param exchange The exception
     * @throws Throwable
     */
    void acceptInternal(HttpServerExchange exchange) throws Throwable;
}
