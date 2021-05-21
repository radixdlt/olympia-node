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

package com.radixdlt.api.controller;

import com.radixdlt.api.Controller;
import com.radixdlt.api.server.JsonRpcServer;

import io.undertow.server.RoutingHandler;

public final class SystemController implements Controller {
	private final JsonRpcServer jsonRpcServer;

	public SystemController(JsonRpcServer jsonRpcServer) {
		this.jsonRpcServer = jsonRpcServer;
	}

	@Override
	public void configureRoutes(final RoutingHandler handler) {
		handler.post("/system", jsonRpcServer::handleHttpRequest);
		handler.post("/system/", jsonRpcServer::handleHttpRequest);
	}
}
