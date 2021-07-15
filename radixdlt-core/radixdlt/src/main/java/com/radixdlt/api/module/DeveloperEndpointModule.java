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

package com.radixdlt.api.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.JsonRpcController;
import com.radixdlt.api.handler.DeveloperHandler;
import com.radixdlt.api.qualifier.DeveloperEndpoint;
import com.radixdlt.api.qualifier.NodeServer;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class DeveloperEndpointModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(DeveloperHandler.class).in(Scopes.SINGLETON);
	}

	@NodeServer
	@ProvidesIntoMap
	@StringMapKey("/developer")
	public Controller devControllerController(@DeveloperEndpoint JsonRpcServer jsonRpcServer) {
		return new JsonRpcController(jsonRpcServer);
	}

	@DeveloperEndpoint
	@Provides
	public JsonRpcServer rpcServer(@DeveloperEndpoint Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@DeveloperEndpoint
	@ProvidesIntoMap
	@StringMapKey("developer.build_genesis")
	public JsonRpcHandler developerBuildGenesis(DeveloperHandler developerHandler) {
		return developerHandler::handleGenesisConstruction;
	}

	@DeveloperEndpoint
	@ProvidesIntoMap
	@StringMapKey("developer.parse_transaction")
	public JsonRpcHandler developerParseTransaction(DeveloperHandler developerHandler) {
		return developerHandler::handleParseTxn;
	}

	@DeveloperEndpoint
	@ProvidesIntoMap
	@StringMapKey("developer.parse_address")
	public JsonRpcHandler developerParseAddress(DeveloperHandler developerHandler) {
		return developerHandler::handleParseAddress;
	}


	@DeveloperEndpoint
	@ProvidesIntoMap
	@StringMapKey("developer.create_address")
	public JsonRpcHandler developerCreateAddress(DeveloperHandler developerHandler) {
		return developerHandler::handleCreateAddress;
	}
}
