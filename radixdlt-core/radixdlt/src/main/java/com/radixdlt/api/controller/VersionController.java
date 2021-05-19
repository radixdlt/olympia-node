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

import org.json.JSONObject;
import org.radix.universe.system.LocalSystem;

import com.radixdlt.api.Controller;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;

import static org.radix.Radix.SYSTEM_VERSION_KEY;

import static com.radixdlt.api.RestUtils.respond;

public class VersionController implements Controller {
	private final JSONObject versionData;

	public VersionController(LocalSystem localSystem) {
		var versionData = new JSONObject(localSystem.getInfo().get(SYSTEM_VERSION_KEY));
		var versionString = versionData.getString()
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.get("/version", this::handleVersionRequest);
		handler.get("/version/", this::handleVersionRequest);
	}

	private void handleVersionRequest(HttpServerExchange exchange) {
		respond(exchange, versionData);
	}
}
