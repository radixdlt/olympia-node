/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessWithSyncRunner;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import com.radixdlt.environment.rx.RemoteEvent;
import com.radixdlt.ledger.DtoCommandsAndProof;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.ledger.LedgerUpdate;
import com.radixdlt.sync.LocalSyncRequest;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor;
import com.radixdlt.sync.LocalSyncServiceAccumulatorProcessor.SyncInProgress;
import com.radixdlt.sync.RemoteSyncResponseValidatorSetVerifier;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;

import java.util.Set;

/**
 * A sync runner which doesn't involve epochs
 */
public class MockedSyncRunnerModule extends AbstractModule {
	@Override
	public void configure() {
		bind(new TypeLiteral<RemoteEventProcessor<DtoCommandsAndProof>>() { })
			.to(RemoteSyncResponseValidatorSetVerifier.class).in(Scopes.SINGLETON);
		bind(LocalSyncServiceAccumulatorProcessor.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoMap
	@StringMapKey("sync")
	private ModuleRunner syncRunner(
		@Self BFTNode self,
		Observable<LocalSyncRequest> localSyncRequests,
		EventProcessor<LocalSyncRequest> syncRequestEventProcessor,
		Observable<LocalSyncServiceAccumulatorProcessor.SyncInProgress> syncTimeouts,
		EventProcessor<LocalSyncServiceAccumulatorProcessor.SyncInProgress> syncTimeoutProcessor,
		Observable<LedgerUpdate> ledgerUpdates,
		@ProcessWithSyncRunner Set<EventProcessor<LedgerUpdate>> ledgerUpdateProcessors,
		Flowable<RemoteEvent<DtoLedgerHeaderAndProof>> remoteSyncRequests,
		RemoteEventProcessor<DtoLedgerHeaderAndProof> remoteSyncServiceProcessor,
		Flowable<RemoteEvent<DtoCommandsAndProof>> remoteSyncResponses,
		RemoteEventProcessor<DtoCommandsAndProof> responseProcessor
	) {
		return ModuleRunnerImpl.builder()
				.add(localSyncRequests, syncRequestEventProcessor)
				.add(syncTimeouts, syncTimeoutProcessor)
				.add(ledgerUpdates, e -> ledgerUpdateProcessors.forEach(p -> p.process(e)))
				.add(remoteSyncRequests, remoteSyncServiceProcessor)
				.add(remoteSyncResponses, responseProcessor)
				.build("SyncManager " + self);
	}

	@ProvidesIntoSet
	@ProcessWithSyncRunner
	private EventProcessor<LedgerUpdate> epochsLedgerUpdateEventProcessor(
		LocalSyncServiceAccumulatorProcessor localSyncServiceAccumulatorProcessor
	) {
		return localSyncServiceAccumulatorProcessor::processLedgerUpdate;
	}

	@Provides
	public EventProcessor<LocalSyncRequest> localSyncRequestEventProcessor(
		LocalSyncServiceAccumulatorProcessor localSyncServiceAccumulatorProcessor
	) {
		return localSyncServiceAccumulatorProcessor.localSyncRequestEventProcessor();
	}


	@Provides
	private EventProcessor<SyncInProgress> syncInProgressEventProcessor(
		LocalSyncServiceAccumulatorProcessor localSyncServiceAccumulatorProcessor
	) {
		return localSyncServiceAccumulatorProcessor.syncTimeoutProcessor();
	}
}
