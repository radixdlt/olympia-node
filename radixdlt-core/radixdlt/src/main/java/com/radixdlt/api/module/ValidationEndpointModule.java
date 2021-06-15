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

package com.radixdlt.api.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.JsonRpcController;
import com.radixdlt.api.handler.ValidationHandler;
import com.radixdlt.api.qualifier.NodeServer;
import com.radixdlt.api.qualifier.ValidationEndpoint;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class ValidationEndpointModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(ValidationHandler.class).in(Scopes.SINGLETON);
	}

	@NodeServer
	@ProvidesIntoMap
	@StringMapKey("/validation")
	public Controller validationController(@ValidationEndpoint JsonRpcServer jsonRpcServer) {
		return new JsonRpcController(jsonRpcServer);
	}

	@ValidationEndpoint
	@Provides
	public JsonRpcServer rpcServer(@ValidationEndpoint Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@ValidationEndpoint
	@ProvidesIntoMap
	@StringMapKey("validation.get_node_info")
	public JsonRpcHandler getNodeInfo(ValidationHandler validationHandler) {
		return validationHandler::handleGetNodeInfo;
	}

	@ValidationEndpoint
	@ProvidesIntoMap
	@StringMapKey("validation.get_current_epoch_data")
	public JsonRpcHandler getCurrentEpochData(ValidationHandler validationHandler) {
		return validationHandler::handleGetCurrentEpochData;
	}

	@ValidationEndpoint
	@ProvidesIntoMap
	@StringMapKey("validation.get_next_epoch_data")
	public JsonRpcHandler getNextEpochData(ValidationHandler validationHandler) {
		return validationHandler::handleGetNextEpochData;
	}
}
