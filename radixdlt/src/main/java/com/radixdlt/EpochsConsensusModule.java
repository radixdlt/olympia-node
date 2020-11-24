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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.LocalTimeoutSender;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.consensus.epoch.ProposerElectionFactory;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.epoch.BFTSyncFactory;
import com.radixdlt.consensus.epoch.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerStateFactory;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.PendingViewTimeouts;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.VoteSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.LocalGetVerticesRequest;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.ScheduledEventDispatcher;

import com.radixdlt.store.LastEpochProof;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Comparator;
import java.util.Random;

/**
 * Module which allows for consensus to have multiple epochs
 */
public class EpochsConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;

	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
	}

	@Provides
	private EventProcessor<BFTUpdate> bftUpdateProcessor(EpochManager epochManager) {
		return epochManager::processBFTUpdate;
	}

	@Provides
	private EventProcessor<LocalGetVerticesRequest> bftSyncTimeoutProcessor(EpochManager epochManager) {
		return epochManager::processGetVerticesLocalTimeout;
	}

	@Provides
	private PacemakerTimeoutSender initialTimeoutSender(LocalTimeoutSender localTimeoutSender, EpochChange initialEpoch) {
		return (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(initialEpoch.getEpoch(), view), ms);
	}

	@Provides
	private EpochChange initialEpoch(
		@LastEpochProof VerifiedLedgerHeaderAndProof proof,
		BFTConfiguration initialBFTConfig
	) {
		return new EpochChange(proof, initialBFTConfig);
	}

	@Provides
	private ProposerElectionFactory proposerElectionFactory() {
		return validatorSet -> new WeightedRotatingLeaders(
			validatorSet,
			Comparator.comparing(v -> v.getNode().getKey().euid()),
			ROTATING_WEIGHTED_LEADERS_CACHE_SIZE
		);
	}

	@Provides
	private EventProcessor<EpochViewUpdate> epochViewUpdateEventProcessor(EpochManager epochManager) {
		return epochManager.epochViewUpdateEventProcessor();
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<ViewUpdate> initialViewUpdateSender(
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher,
		PacemakerTimeoutSender timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		EpochChange initialEpoch
	) {
		return viewUpdate -> {
			epochViewUpdateEventDispatcher.dispatch(new EpochViewUpdate(initialEpoch.getEpoch(), viewUpdate));
			long timeout = timeoutCalculator.timeout(viewUpdate.uncommittedViewsCount());
			timeoutSender.scheduleTimeout(viewUpdate.getCurrentView(), timeout);
		};
	}

	@Provides
	private PacemakerStateFactory pacemakerStateFactory(
		LocalTimeoutSender localTimeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher
	) {
		return epoch ->
			new PacemakerState(viewUpdate -> {
				epochViewUpdateEventDispatcher.dispatch(new EpochViewUpdate(epoch, viewUpdate));
				long timeout = timeoutCalculator.timeout(viewUpdate.uncommittedViewsCount());
				localTimeoutSender.scheduleTimeout(new LocalTimeout(epoch, viewUpdate.getCurrentView()), timeout);
			});
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	EventProcessor<LocalTimeoutOccurrence> initialEpochsTimeoutProcessor(
		EpochChange initialEpoch,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutDispatcher
	) {
		return timeoutOccurrence -> {
			EpochView epochView = new EpochView(initialEpoch.getEpoch(), timeoutOccurrence.getView());
			timeoutDispatcher.dispatch(new EpochLocalTimeoutOccurrence(epochView, timeoutOccurrence.getLeader()));
		};
	}

	@Provides
	private PacemakerFactory pacemakerFactory(
		@Self BFTNode self,
		SystemCounters counters,
		VoteSender voteSender,
		ProposalBroadcaster proposalBroadcaster,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutEventDispatcher,
		LocalTimeoutSender localTimeoutSender
	) {
		return (
			validatorSet,
			vertexStore,
			pacemakerState,
			timeoutCalculator,
			safetyRules,
			proposerElection,
			epoch
		) -> {
			PendingViewTimeouts pendingViewTimeouts = new PendingViewTimeouts();
			return new Pacemaker(
				self,
				counters,
				pendingViewTimeouts,
				validatorSet,
				vertexStore,
				safetyRules,
				voteSender,
				timeout -> {
					EpochView epochView = new EpochView(epoch, timeout.getView());
					timeoutEventDispatcher.dispatch(new EpochLocalTimeoutOccurrence(epochView, timeout.getLeader()));
				},
				pacemakerState,
				(view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(epoch, view), ms),
				timeoutCalculator,
				nextCommandGenerator,
				proposalBroadcaster,
				proposerElection,
				hasher
			);
		};
	}

	@Provides
	private BFTSyncRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		SyncVerticesResponseSender syncVerticesResponseSender
	) {
		return vertexStore -> new VertexStoreBFTSyncRequestProcessor(vertexStore, syncVerticesResponseSender);
	}

	@Provides
	private BFTSyncFactory bftSyncFactory(
		SyncVerticesRequestSender requestSender,
		EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
		ScheduledEventDispatcher<LocalGetVerticesRequest> timeoutDispatcher,
		BFTConfiguration configuration,
		SystemCounters counters,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis
	) {
		return (vertexStore, pacemakerState) -> new BFTSync(
			vertexStore,
			pacemakerState,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			(node, request)  -> {
				counters.increment(CounterType.BFT_SYNC_REQUESTS_SENT);
				requestSender.sendGetVerticesRequest(node, request);
			},
			syncLedgerRequestSender,
			timeoutDispatcher,
			configuration.getGenesisHeader(),
			random,
			bftSyncPatienceMillis
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		EventDispatcher<BFTUpdate> updateSender,
		EventDispatcher<BFTCommittedUpdate> committedDispatcher,
		SystemCounters counters,
		Ledger ledger,
		VertexStoreEventSender vertexStoreEventSender
	) {
		return (genesisVertex, genesisQC) -> VertexStore.create(
			genesisVertex,
			genesisQC,
			ledger,
			updateSender,
			committedDispatcher,
			vertexStoreEventSender,
			counters
		);
	}
}
