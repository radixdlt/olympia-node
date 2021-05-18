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

import org.radix.universe.system.LocalSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.api.Controller;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.server.JsonRpcServer;
import com.radixdlt.api.qualifier.System;
import com.radixdlt.api.controller.SystemController;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.systeminfo.InMemorySystemInfo;

import java.util.Map;

public class SystemEndpointModule extends AbstractModule {
	@System
	@Provides
	public JsonRpcServer systemRpcHandler(@System Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	//TODO: remove/rework/replace
	@ProvidesIntoSet
	public Controller constructController(
		@System JsonRpcServer jsonRpcServer,
		InMemorySystemInfo inMemorySystemInfo,
		LocalSystem localSystem,
		AddressBook addressBook,
		@Genesis VerifiedTxnsAndProof genesis
	) {
		return new SystemController(jsonRpcServer, inMemorySystemInfo, localSystem, addressBook, genesis);
	}

	//TODO: replace with system endpoints
//	@Construct
//	@ProvidesIntoMap
//	@StringMapKey("transaction.build")
//	public JsonRpcHandler transactionBuild(ConstructionHandler constructionHandler) {
//		return constructionHandler::handleBuildTransaction;
//	}
//
//	@Construct
//	@ProvidesIntoMap
//	@StringMapKey("transaction.finalize")
//	public JsonRpcHandler finalizeTransaction(ConstructionHandler constructionHandler) {
//		return constructionHandler::handleFinalizeTransaction;
//	}
//
//	@Construct
//	@ProvidesIntoMap
//	@StringMapKey("transaction.submit")
//	public JsonRpcHandler transactionSubmit(ConstructionHandler constructionHandler) {
//		return constructionHandler::handleSubmitTransaction;
//	}
}
