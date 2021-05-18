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
import com.radixdlt.api.qualifier.AtArchive;
import com.radixdlt.api.server.JsonRpcServer;
import com.radixdlt.api.handler.ConstructionHandler;
import com.radixdlt.api.qualifier.Construct;
import com.radixdlt.api.construction.TxnParser;
import com.radixdlt.api.controller.ConstructController;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.mempool.MempoolAdd;
import com.radixdlt.store.TxnIndex;

import java.util.Map;

public class ConstructEndpointModule extends AbstractModule {
	@Construct
	@Provides
	public JsonRpcServer archiveRpcHandler(@Construct Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@AtArchive
	@ProvidesIntoSet
	public Controller constructController(
		TxnIndex txnIndex, TxnParser txnParser,
		EventDispatcher<MempoolAdd> mempoolAddEventDispatcher,
		@Construct JsonRpcServer jsonRpcServer
	) {
		return new ConstructController(txnIndex, txnParser, mempoolAddEventDispatcher, jsonRpcServer);
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("transaction.build")
	public JsonRpcHandler transactionBuild(ConstructionHandler constructionHandler) {
		return constructionHandler::handleBuildTransaction;
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("transaction.finalize")
	public JsonRpcHandler finalizeTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleFinalizeTransaction;
	}

	@Construct
	@ProvidesIntoMap
	@StringMapKey("transaction.submit")
	public JsonRpcHandler transactionSubmit(ConstructionHandler constructionHandler) {
		return constructionHandler::handleSubmitTransaction;
	}
}
