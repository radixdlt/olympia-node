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
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.controller.ValidationController;
import com.radixdlt.api.qualifier.Validation;
import com.radixdlt.api.server.JsonRpcServer;
import com.radixdlt.application.NodeApplicationRequest;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.identifiers.REAddr;
import com.radixdlt.statecomputer.LedgerAndBFTProof;

import java.util.Map;

public class ValidationEndpointModule extends AbstractModule {
	@Validation
	@Provides
	public JsonRpcServer rpcServer(@Validation Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	//TODO: remove/rework/replace
	@Validation
	@ProvidesIntoSet
	public Controller validationController(
		@Validation JsonRpcServer jsonRpcServer,
		@Self REAddr account,
		@Self ECPublicKey bftKey,
		RadixEngine<LedgerAndBFTProof> radixEngine,
		EventDispatcher<NodeApplicationRequest> nodeApplicationRequestEventDispatcher
	) {
		return new ValidationController(jsonRpcServer, account, bftKey, radixEngine, nodeApplicationRequestEventDispatcher);
	}

	//TODO: replace with validation endpoints
//	@Validation
//	@ProvidesIntoMap
//	@StringMapKey("transaction.build")
//	public JsonRpcHandler transactionBuild(ConstructionHandler constructionHandler) {
//		return constructionHandler::handleBuildTransaction;
//	}
}
