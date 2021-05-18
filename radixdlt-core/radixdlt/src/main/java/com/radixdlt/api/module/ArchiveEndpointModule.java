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
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.handler.ArchiveHandler;
import com.radixdlt.api.qualifier.Archive;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class ArchiveEndpointModule extends AbstractModule {
	@Archive
	@Provides
	public JsonRpcServer archiveRpcHandler(@Archive Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("token.native")
	public JsonRpcHandler tokenNative(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNativeToken;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("token.info")
	public JsonRpcHandler tokenInfo(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTokenInfo;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("address.balances")
	public JsonRpcHandler addressBalances(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTokenBalances;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("address.stakes")
	public JsonRpcHandler addressStakes(ArchiveHandler archiveHandler) {
		return archiveHandler::handleStakePositions;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("address.unstakes")
	public JsonRpcHandler addressUnstakes(ArchiveHandler archiveHandler) {
		return archiveHandler::handleUnstakePositions;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("address.transactions")
	public JsonRpcHandler addressTransactions(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTransactionHistory;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("transaction.info")
	public JsonRpcHandler transactionInfo(ArchiveHandler archiveHandler) {
		return archiveHandler::handleLookupTransaction;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("transaction.status")
	public JsonRpcHandler transactionStatus(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTransactionStatus;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("validator.list")
	public JsonRpcHandler validatorList(ArchiveHandler archiveHandler) {
		return archiveHandler::handleValidators;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("validator.info")
	public JsonRpcHandler validatorInfo(ArchiveHandler archiveHandler) {
		return archiveHandler::handleLookupValidator;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.id")
	public JsonRpcHandler networkId(ArchiveHandler archiveHandler) {
		return archiveHandler::handleUniverseMagic;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.throughput")
	public JsonRpcHandler networkThroughput(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNetworkTransactionThroughput;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.demand")
	public JsonRpcHandler networkDemand(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNetworkTransactionDemand;
	}
}
