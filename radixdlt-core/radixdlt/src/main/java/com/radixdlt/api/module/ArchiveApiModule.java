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

import com.google.inject.multibindings.Multibinder;
import com.radixdlt.api.store.berkeley.BerkeleyValidatorUptimeArchiveStore;
import com.radixdlt.store.berkeley.BerkeleyAdditionalStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.EndpointConfig;
import com.radixdlt.ModuleRunner;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.server.ArchiveHttpServer;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.service.TransactionStatusService;
import com.radixdlt.api.store.ClientApiStore;
import com.radixdlt.api.store.berkeley.BerkeleyClientApiStore;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.mempool.MempoolAddSuccess;
import com.radixdlt.statecomputer.REOutput;

import java.util.List;

public class ArchiveApiModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	private final List<EndpointConfig> endpoints;

	public ArchiveApiModule(List<EndpointConfig> endpoints) {
		this.endpoints = endpoints;
	}

	@Override
	public void configure() {
		bind(ClientApiStore.class).to(BerkeleyClientApiStore.class).in(Scopes.SINGLETON);

		endpoints.forEach(ep -> {
			log.info("Enabling /{} endpoint", ep.name());
			install(ep.module().get());
		});

		MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class)
			.addBinding(Runners.ARCHIVE_API)
			.to(ArchiveHttpServer.class);

		bind(BerkeleyValidatorUptimeArchiveStore.class).in(Scopes.SINGLETON);
		Multibinder.newSetBinder(binder(), BerkeleyAdditionalStore.class)
			.addBinding().to(BerkeleyValidatorUptimeArchiveStore.class);

		bind(ArchiveHttpServer.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	private EventProcessorOnRunner<?> atomsCommittedToLedgerEventProcessorApiStore(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			REOutput.class,
			clientApiStore.atomsCommittedToLedgerEventProcessor()
		);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> ledgerUpdateToLedgerApiStore(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			LedgerUpdate.class,
			clientApiStore.ledgerUpdateProcessor()
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
	public EventProcessorOnRunner<?> ledgerUpdateToLedgerTransactionStatus(TransactionStatusService transactionStatusService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			LedgerUpdate.class,
			transactionStatusService.ledgerUpdateProcessor()
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

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> queueFlushProcessor(ClientApiStore clientApiStore) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledQueueFlush.class,
			clientApiStore.queueFlushProcessor()
		);
	}
}
