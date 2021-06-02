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
import com.radixdlt.api.controller.ArchiveController;
import com.radixdlt.api.handler.ArchiveAccountHandler;
import com.radixdlt.api.handler.ArchiveTransactionsHandler;
import com.radixdlt.api.handler.ArchiveNetworkHandler;
import com.radixdlt.api.handler.ArchiveTokenHandler;
import com.radixdlt.api.handler.ArchiveValidationHandler;
import com.radixdlt.api.qualifier.Archive;
import com.radixdlt.api.qualifier.AtArchive;
import com.radixdlt.api.server.JsonRpcServer;

import java.util.Map;

public class ArchiveEndpointModule extends AbstractModule {
	@AtArchive
	@ProvidesIntoSet
	public Controller archiveController(@Archive JsonRpcServer jsonRpcServer) {
		return new ArchiveController(jsonRpcServer);
	}

	@Archive
	@Provides
	public JsonRpcServer rpcServer(@Archive Map<String, JsonRpcHandler> additionalHandlers) {
		return new JsonRpcServer(additionalHandlers);
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("account.get_balances")
	public JsonRpcHandler accountGetBalances(ArchiveAccountHandler archiveAccountHandler) {
		return archiveAccountHandler::handleAccountGetBalances;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("account.get_stake_positions")
	public JsonRpcHandler accountGetStakePositions(ArchiveAccountHandler archiveAccountHandler) {
		return archiveAccountHandler::handleAccountGetStakePositions;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("account.get_unstake_positions")
	public JsonRpcHandler accountGetUnstakePositions(ArchiveAccountHandler archiveAccountHandler) {
		return archiveAccountHandler::handleAccountGetUnstakePositions;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("account.get_transaction_history")
	public JsonRpcHandler accountGetTransactionHistory(ArchiveAccountHandler archiveAccountHandler) {
		return archiveAccountHandler::handleAccountGetTransactionHistory;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("transactions.lookup_transaction")
	public JsonRpcHandler transactionsLookupTransaction(ArchiveTransactionsHandler archiveTransactionsHandler) {
		return archiveTransactionsHandler::handleTransactionsLookupTransaction;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("transactions.get_transaction_status")
	public JsonRpcHandler transactionsGetTransactionStatus(ArchiveTransactionsHandler archiveTransactionsHandler) {
		return archiveTransactionsHandler::handleTransactionsGetTransactionStatus;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("tokens.get_native_token")
	public JsonRpcHandler tokensGetNativeToken(ArchiveTokenHandler archiveTokenHandler) {
		return archiveTokenHandler::handleTokensGetNativeToken;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("tokens.get_info")
	public JsonRpcHandler tokensGetInfo(ArchiveTokenHandler archiveTokenHandler) {
		return archiveTokenHandler::handleTokensGetInfo;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("validators.get_next_epoch_set")
	public JsonRpcHandler validatorsGetNextEpochSet(ArchiveValidationHandler archiveValidationHandler) {
		return archiveValidationHandler::handleValidatorsGetNextEpochSet;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("validators.lookup_validator")
	public JsonRpcHandler validatorsLookupValidator(ArchiveValidationHandler archiveValidationHandler) {
		return archiveValidationHandler::handleValidatorsLookupValidator;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.get_id")
	public JsonRpcHandler networkGetId(ArchiveNetworkHandler archiveNetworkHandler) {
		return archiveNetworkHandler::handleNetworkGetId;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.get_throughput")
	public JsonRpcHandler networkGetThroughput(ArchiveNetworkHandler archiveNetworkHandler) {
		return archiveNetworkHandler::handleNetworkGetThroughput;
	}

	@Archive
	@ProvidesIntoMap
	@StringMapKey("network.get_demand")
	public JsonRpcHandler networkGetDemand(ArchiveNetworkHandler archiveNetworkHandler) {
		return archiveNetworkHandler::handleNetworkGetDemand;
	}
}
