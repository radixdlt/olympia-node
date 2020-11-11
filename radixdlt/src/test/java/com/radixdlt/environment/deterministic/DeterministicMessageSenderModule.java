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

package com.radixdlt.environment.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.Synchronous;
import com.radixdlt.environment.deterministic.network.ControlledSender;
import com.radixdlt.epochs.EpochChangeManager.EpochsLedgerUpdateSender;
import com.radixdlt.environment.deterministic.network.DeterministicNetwork.DeterministicSender;
import com.radixdlt.ledger.DtoLedgerHeaderAndProof;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Set;

/**
 * Module that supplies network senders, as well as some other assorted
 * objects used to connect modules in the system.
 */
public class DeterministicMessageSenderModule extends AbstractModule {
	@Override
	protected void configure() {

		bind(ProposalBroadcaster.class).to(DeterministicSender.class);
		bind(ProceedToViewSender.class).to(DeterministicSender.class);
		bind(SyncVerticesRequestSender.class).to(DeterministicSender.class);
		bind(SyncVerticesResponseSender.class).to(DeterministicSender.class);
		bind(BFTUpdateSender.class).to(DeterministicSender.class);
		bind(LocalTimeoutSender.class).to(DeterministicSender.class);
		bind(BFTSyncTimeoutScheduler.class).to(DeterministicSender.class);
		bind(SyncEpochsRPCSender.class).to(DeterministicSender.class);

		// TODO: Remove multibind?
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessor<LocalSyncRequest>>() { }, Synchronous.class);
		Multibinder.newSetBinder(binder(), VertexStoreEventSender.class).addBinding().to(DeterministicSender.class);
		Multibinder.newSetBinder(binder(), EpochsLedgerUpdateSender.class).addBinding().to(DeterministicSender.class);

		bind(DeterministicEpochInfo.class).in(Scopes.SINGLETON);
		bind(DeterministicSender.class).to(ControlledSender.class);

		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	@Synchronous
	EventProcessor<Timeout> timeoutEventProcessor(DeterministicEpochInfo processor) {
		return processor.timeoutEventProcessor();
	}

	@ProvidesIntoSet
	@Synchronous
	EventProcessor<EpochView> epochViewEventProcessor(DeterministicEpochInfo processor) {
		return processor.epochViewEventProcessor();
	}

	@Provides
	EventDispatcher<Timeout> timeoutEventDispatcher(
		@Synchronous Set<EventProcessor<Timeout>> processors
	) {
		return timeout -> processors.forEach(e -> e.process(timeout));
	}

	@Provides
	EventDispatcher<EpochView> epochViewEventDispatcher(
		@Synchronous Set<EventProcessor<EpochView>> processors
	) {
		return epochView -> processors.forEach(e -> e.process(epochView));
	}

	@ProvidesIntoSet
	@Synchronous
	EventProcessor<LocalSyncRequest> controlledSender(ControlledSender controlledSender) {
		return controlledSender::dispatch;
	}

	@Provides
	RemoteEventDispatcher<DtoLedgerHeaderAndProof> syncRequestDispatcher(ControlledSender controlledSender) {
		return controlledSender::dispatch;
	}

	@Provides
	@Singleton
	ControlledSender sender(@Self BFTNode self, ControlledSenderFactory senderFactory) {
		return senderFactory.create(self);
	}
}
