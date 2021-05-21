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
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.ConstructController;
import com.radixdlt.api.handler.ConstructionHandler;
import com.radixdlt.api.qualifier.AtArchive;
import com.radixdlt.api.qualifier.Construct;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class ConstructEndpointModule extends AbstractModule {
	@AtArchive
	@ProvidesIntoSet
	public Controller constructController(@Construct JsonRpcServer jsonRpcServer) {
		return new ConstructController(jsonRpcServer);
	}

	@Construct
	@Provides
	public JsonRpcServer rpcServer(@Construct Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("construction.build_transaction")
	public JsonRpcHandler constructionBuildTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleConstructionBuildTransaction;
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("construction.finalize_transaction")
	public JsonRpcHandler constructionFinalizeTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleConstructionFinalizeTransaction;
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("construction.submit_transaction")
	public JsonRpcHandler constructionSubmitTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleConstructionSubmitTransaction;
	}
}
