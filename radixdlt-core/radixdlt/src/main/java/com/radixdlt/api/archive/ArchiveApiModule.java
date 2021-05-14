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

package com.radixdlt.api.archive;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.archive.handler.ConstructionHandler;
import com.radixdlt.api.archive.handler.ArchiveHandler;
import com.radixdlt.api.archive.service.NetworkInfoService;
import com.radixdlt.api.archive.service.ScheduledCacheCleanup;
import com.radixdlt.api.archive.service.ScheduledStatsCollecting;
import com.radixdlt.api.archive.service.TransactionStatusService;
import com.radixdlt.api.archive.store.ClientApiStore;
import com.radixdlt.api.archive.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.api.archive.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;

public class ArchiveApiModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.ARCHIVE_API)
			.to(ArchiveServer.class);
		bind(ArchiveServer.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder
			.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);
		bind(TransactionStatusService.class).in(Scopes.SINGLETON);
		bind(NetworkInfoService.class).in(Scopes.SINGLETON);

		eventBinder.addBinding().toInstance(TxnsCommittedToLedger.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);
		eventBinder.addBinding().toInstance(ScheduledStatsCollecting.class);
	}

	@ProvidesIntoMap
	@StringMapKey("radix.networkId")
	public JsonRpcHandler universeMagicHandler(ArchiveHandler archiveHandler) {
		return archiveHandler::handleUniverseMagic;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.nativeToken")
	public JsonRpcHandler nativeTokenHandler(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNativeToken;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.tokenBalances")
	public JsonRpcHandler tokenBalancesHandler(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTokenBalances;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.transactionHistory")
	public JsonRpcHandler transactionHistory(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTransactionHistory;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.lookupTransaction")
	public JsonRpcHandler lookupTransaction(ArchiveHandler archiveHandler) {
		return archiveHandler::handleLookupTransaction;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.statusOfTransaction")
	public JsonRpcHandler transactionStatus(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTransactionStatus;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.tokenInfo")
	public JsonRpcHandler tokenInfo(ArchiveHandler archiveHandler) {
		return archiveHandler::handleTokenInfo;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.buildTransaction")
	public JsonRpcHandler buildTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleBuildTransaction;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.finalizeTransaction")
	public JsonRpcHandler finalizeTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleFinalizeTransaction;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.submitTransaction")
	public JsonRpcHandler submitTransaction(ConstructionHandler constructionHandler) {
		return constructionHandler::handleSubmitTransaction;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.validators")
	public JsonRpcHandler validators(ArchiveHandler archiveHandler) {
		return archiveHandler::handleValidators;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.stakePositions")
	public JsonRpcHandler stakePositions(ArchiveHandler archiveHandler) {
		return archiveHandler::handleStakePositions;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.unstakePositions")
	public JsonRpcHandler unstakePositions(ArchiveHandler archiveHandler) {
		return archiveHandler::handleUnstakePositions;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.lookupValidator")
	public JsonRpcHandler lookupValidator(ArchiveHandler archiveHandler) {
		return archiveHandler::handleLookupValidator;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.networkTransactionThroughput")
	public JsonRpcHandler networkTransactionThroughput(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNetworkTransactionThroughput;
	}

	@ProvidesIntoMap
	@StringMapKey("radix.networkTransactionDemand")
	public JsonRpcHandler networkTransactionDemand(ArchiveHandler archiveHandler) {
		return archiveHandler::handleNetworkTransactionDemand;
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> atomsCommittedToLedgerEventProcessorBerkeleyClientApi(
		BerkeleyClientApiStore berkeleyClientApiStore
	) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			TxnsCommittedToLedger.class,
			berkeleyClientApiStore.atomsCommittedToLedgerEventProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> queueFlushProcessor(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledQueueFlush.class,
			clientApiStore.queueFlushProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> cacheCleanupEventProcessor(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledCacheCleanup.class,
			transactionStatusService.cacheCleanupEventProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> networkInfoService(NetworkInfoService networkInfoService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledStatsCollecting.class,
			networkInfoService.updateStats()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> atomsCommittedToLedgerTransactionStatus(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			TxnsCommittedToLedger.class,
			transactionStatusService.atomsCommittedToLedgerEventProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> mempoolAddFailureEventProcessor(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			MempoolAddFailure.class,
			transactionStatusService.mempoolAddFailureEventProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> mempoolAddSuccessEventProcessor(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			MempoolAddSuccess.class,
			transactionStatusService.mempoolAddSuccessEventProcessor()
		);
	}
}
