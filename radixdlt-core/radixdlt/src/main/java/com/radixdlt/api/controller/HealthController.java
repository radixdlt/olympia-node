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

package com.radixdlt.api.controller;

import com.radixdlt.api.Controller;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static com.radixdlt.api.JsonRpcUtil.jsonObject;
import static com.radixdlt.api.RestUtils.respond;

public class HealthController implements Controller {
	private final SystemCounters counters;

	public HealthController(SystemCounters counters) {
		this.counters = counters;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.get("/health", this::handleHealthRequest);
		handler.get("/health/", this::handleHealthRequest);
	}

	private void handleHealthRequest(HttpServerExchange exchange) {
		respond(exchange, jsonObject().put("status", isSynced() ? "UP" : "SYNCING"));
	}

	private boolean isSynced() {
		return counters.get(CounterType.LEDGER_STATE_VERSION) == counters.get(CounterType.SYNC_TARGET_STATE_VERSION);
	}
}
