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

import io.undertow.server.HttpServerExchange;
import io.undertow.util.StatusCodes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A shortcut interface for creating lambda methods that handle {@link HttpServerExchange}s in a safe way
 */
@FunctionalInterface
interface ManagedHttpExchangeConsumer {
    Logger logger = LogManager.getLogger();

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
