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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.EndpointConfig;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.JsonRpcHandler;
import com.radixdlt.api.server.ArchiveServer;
import com.radixdlt.api.server.JsonRpcServer;
import com.radixdlt.api.handler.ArchiveHandler;
import com.radixdlt.api.qualifier.Archive;
import com.radixdlt.api.service.NetworkInfoService;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.service.ScheduledStatsCollecting;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.api.archive.store.ClientApiStore;
import com.radixdlt.api.archive.store.berkeley.ScheduledQueueFlush;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.TxnsCommittedToLedger;

import java.util.List;
import java.util.Map;

public class ArchiveApiModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	private final List<EndpointConfig> endpoints;

	public ArchiveApiModule(List<EndpointConfig> endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public void configure() {
		if (endpoints.isEmpty()) {
			return;
		}

		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.ARCHIVE_API)
			.to(ArchiveServer.class);
		bind(ArchiveServer.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder
			.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		bind(TransactionStatusService.class).in(Scopes.SINGLETON);
		bind(NetworkInfoService.class).in(Scopes.SINGLETON);

		eventBinder.addBinding().toInstance(TxnsCommittedToLedger.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);
		eventBinder.addBinding().toInstance(ScheduledStatsCollecting.class);

		endpoints.forEach(ep -> {
			log.info("Enabling /{} endpoint", ep.name());
			install(ep.module().get());
		});
//
//		if (properties.get(API_ARCHIVE, false)) {
//			log.info("Enabling /archive API");
//			install(new ArchiveApiModule());
//		}
//
//		if (properties.get(API_CONSTRUCT, false)) {
//			log.info("Enabling /construct API");
//			install(new ConstructApiModule());
//		}
//
//		if (properties.get(API_SYSTEM, false)) {
//			//TODO: finish it
//			log.info("Enabling /system API");
//			install(new SystemApiModule());
//		}
//
//		if (properties.get(API_ACCOUNT, false)) {
//			//TODO: finish it
//			log.info("Enabling /account API");
//			//install(new AccountApiModule());
//		}
//
//		if (properties.get(API_VALIDATOR, false)) {
//			log.info("Enabling /validator API");
//			install(new ValidatorApiModule());
//		}
//
//		if (properties.get(API_UNIVERSE, false)) {
//			log.info("Enabling /universe API");
//			var controllers = Multibinder.newSetBinder(binder(), Controller.class);
//			controllers.addBinding().to(UniverseController.class).in(Scopes.SINGLETON);
//		}
//
//		if (properties.get(API_FAUCET, false)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /faucet API");
//			install(new FaucetModule());
//		}
//
//		if (properties.get(API_CHAOS, false)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /chaos API");
//			install(new ChaosModule());
//		}
//
//		if (properties.get(API_HEALTH, true)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /chaos API");
//			//install(new HealthModule());
//		}
//
//		if (properties.get(API_VERSION, true)) {
//			//TODO: disable on mainnet
//			log.info("Enabling /version API");
//			//install(new VersionModule());
//		}
	}

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
