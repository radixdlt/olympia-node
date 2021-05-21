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
import com.radixdlt.api.controller.SystemController;
import com.radixdlt.api.handler.SystemHandler;
import com.radixdlt.api.qualifier.AtNode;
import com.radixdlt.api.qualifier.System;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class SystemEndpointModule extends AbstractModule {
	@System
	@Provides
	public JsonRpcServer rpcServer(@System Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@AtNode
	@ProvidesIntoSet
	public Controller systemController(@System JsonRpcServer jsonRpcServer) {
		return new SystemController(jsonRpcServer);
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("api.get_configuration")
	public JsonRpcHandler apiGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::apiGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("api.get_data")
	public JsonRpcHandler apiGetData(SystemHandler systemHandler) {
		return systemHandler::apiGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("bft.get_configuration")
	public JsonRpcHandler bftGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::bftGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("bft.get_data")
	public JsonRpcHandler bftGetData(SystemHandler systemHandler) {
		return systemHandler::bftGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("mempool.get_configuration")
	public JsonRpcHandler mempoolGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::mempoolGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("mempool.get_data")
	public JsonRpcHandler mempoolGetData(SystemHandler systemHandler) {
		return systemHandler::mempoolGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("ledger.get_latest_proof")
	public JsonRpcHandler ledgerGetLatestProof(SystemHandler systemHandler) {
		return systemHandler::ledgerGetLatestProof;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("ledger.get_latest_epoch_proof")
	public JsonRpcHandler ledgerGetLatestEpochProof(SystemHandler systemHandler) {
		return systemHandler::ledgerGetLatestEpochProof;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("radix_engine.get_configuration")
	public JsonRpcHandler radixEngineGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::radixEngineGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("radix_engine.get_data")
	public JsonRpcHandler radixEngineGetData(SystemHandler systemHandler) {
		return systemHandler::radixEngineGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("sync.get_configuration")
	public JsonRpcHandler syncGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::syncGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("sync.get_data")
	public JsonRpcHandler syncGetData(SystemHandler systemHandler) {
		return systemHandler::syncGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("networking.get_configuration")
	public JsonRpcHandler networkingGetConfiguration(SystemHandler systemHandler) {
		return systemHandler::networkingGetConfiguration;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("networking.get_peers")
	public JsonRpcHandler networkingGetPeers(SystemHandler systemHandler) {
		return systemHandler::networkingGetPeers;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("networking.get_data")
	public JsonRpcHandler networkingGetData(SystemHandler systemHandler) {
		return systemHandler::networkingGetData;
	}

	@System
	@ProvidesIntoMap
	@StringMapKey("checkpoints.get_checkpoints")
	public JsonRpcHandler checkpointsGetCheckpoints(SystemHandler systemHandler) {
		return systemHandler::checkpointsGetCheckpoints;
	}
}
