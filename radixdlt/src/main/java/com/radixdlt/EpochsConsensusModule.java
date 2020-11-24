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
import com.radixdlt.consensus.epoch.LocalTimeoutSender;
import com.radixdlt.consensus.epoch.LocalViewUpdate;
import com.radixdlt.consensus.epoch.LocalViewUpdateSender;
import com.radixdlt.consensus.epoch.LocalViewUpdateSenderFactory;
import com.radixdlt.consensus.epoch.LocalViewUpdateSenderWithTimeout;
import com.radixdlt.consensus.epoch.ProposerElectionFactory;
import com.radixdlt.consensus.LocalTimeoutOccurrence;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.epoch.BFTSyncFactory;
import com.radixdlt.consensus.epoch.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.PacemakerInfoSender;
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
import java.util.function.Consumer;

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
	private PacemakerState.ViewUpdateSender initialViewUpdateSender(
		EventDispatcher<EpochView> epochViewEventDispatcher,
		PacemakerTimeoutSender timeoutSender,
		LocalViewUpdateSender localViewUpdateSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		EpochChange initialEpoch
	) {
		return (viewUpdate) -> {
			epochViewEventDispatcher.dispatch(EpochView.of(initialEpoch.getEpoch(), viewUpdate.getCurrentView()));
			localViewUpdateSender.sendLocalViewUpdate(new LocalViewUpdate(initialEpoch.getEpoch(), viewUpdate));
			long timeout = timeoutCalculator.timeout(viewUpdate.uncommittedViewsCount());

			timeoutSender.scheduleTimeout(viewUpdate.getCurrentView(), timeout);
		};
	}

	@Provides
	public PacemakerInfoSender pacemakerInfoSender(
		EventDispatcher<EpochView> epochViewEventDispatcher,
		EpochChange initialEpoch
	) {
		return new PacemakerInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochViewEventDispatcher.dispatch(EpochView.of(initialEpoch.getEpoch(), view));
			}
		};
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
	private PacemakerStateFactory pacemakerStateFactory() {
		return PacemakerState::new;
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	EventProcessor<View> initialEventProcessor(
		EpochChange initialEpoch,
		ProposerElection initialProposerElection,
		EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher
	) {
		return view -> {
			EpochView epochView = new EpochView(initialEpoch.getEpoch(), view);
			BFTNode leader = initialProposerElection.getProposer(view);
			timeoutDispatcher.dispatch(new LocalTimeoutOccurrence(epochView, leader));
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
		EventDispatcher<LocalTimeoutOccurrence> timeoutEventDispatcher
	) {
		return (
			validatorSet,
			vertexStore,
			pacemakerState,
			timeoutSender,
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
				view -> {
					EpochView epochView = new EpochView(epoch, view);
					BFTNode leader = proposerElection.getProposer(view);
					timeoutEventDispatcher.dispatch(new LocalTimeoutOccurrence(epochView, leader));
				},
				pacemakerState,
				timeoutSender,
				timeoutCalculator,
				nextCommandGenerator,
				proposalBroadcaster,
				proposerElection,
				hasher
			);
		};
	}

	@Provides
	private LocalViewUpdateSenderFactory localViewUpdateSenderFactory(
		PacemakerTimeoutCalculator timeoutCalculator,
		EventDispatcher<LocalViewUpdate> localViewUpdateEventDispatcher
	) {
		return (infoSender, timeoutSender) ->
			new LocalViewUpdateSenderWithTimeout(timeoutSender, timeoutCalculator, infoSender, localViewUpdateEventDispatcher);
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
