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

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.epoch.Epoched;
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
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerStateFactory;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ProcessOnDispatch;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.network.TimeSupplier;
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
	private RemoteEventProcessor<GetVerticesRequest> localGetVerticesRequestRemoteEventProcessor(EpochManager epochManager) {
		return epochManager.localGetVerticesRequestRemoteEventProcessor();
	}

	@Provides
	private EventProcessor<BFTInsertUpdate> bftUpdateProcessor(EpochManager epochManager) {
		return epochManager::processBFTUpdate;
	}

	@Provides
	private EventProcessor<BFTRebuildUpdate> bftRebuildUpdateEventProcessor(EpochManager epochManager) {
		return epochManager.bftRebuildUpdateEventProcessor();
	}

	@Provides
	private EventProcessor<VertexRequestTimeout> bftSyncTimeoutProcessor(EpochManager epochManager) {
		return epochManager.timeoutEventProcessor();
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
	private EventProcessor<ScheduledLocalTimeout> initialEpochsTimeoutSender(
		LocalTimeoutSender localTimeoutSender,
		EpochChange initialEpoch
	) {
		return localTimeout -> {
			Epoched<ScheduledLocalTimeout> epochTimeout = Epoched.from(initialEpoch.getEpoch(), localTimeout);
			localTimeoutSender.scheduleTimeout(epochTimeout, localTimeout.millisecondsWaitTime());
		};
	}


	@ProvidesIntoSet
	@ProcessOnDispatch
	private EventProcessor<ViewUpdate> initialViewUpdateSender(
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher,
		EpochChange initialEpoch
	) {
		return viewUpdate -> {
			EpochViewUpdate epochViewUpdate = new EpochViewUpdate(initialEpoch.getEpoch(), viewUpdate);
			epochViewUpdateEventDispatcher.dispatch(epochViewUpdate);
		};
	}

	@Provides
	private PacemakerStateFactory pacemakerStateFactory(
		EventDispatcher<EpochViewUpdate> epochViewUpdateEventDispatcher
	) {
		return (initialView, epoch, proposerElection) ->
			new PacemakerState(initialView, proposerElection, viewUpdate -> {
				EpochViewUpdate epochViewUpdate = new EpochViewUpdate(epoch, viewUpdate);
				epochViewUpdateEventDispatcher.dispatch(epochViewUpdate);
			});
	}

	@ProvidesIntoSet
	@ProcessOnDispatch
	EventProcessor<LocalTimeoutOccurrence> initialEpochsTimeoutProcessor(
		EpochChange initialEpoch,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutDispatcher
	) {
		return timeoutOccurrence ->
			timeoutDispatcher.dispatch(new EpochLocalTimeoutOccurrence(initialEpoch.getEpoch(), timeoutOccurrence));
	}

	@Provides
	private PacemakerFactory pacemakerFactory(
		@Self BFTNode self,
		SystemCounters counters,
		ProposalBroadcaster proposalBroadcaster,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		EventDispatcher<EpochLocalTimeoutOccurrence> timeoutEventDispatcher,
		LocalTimeoutSender localTimeoutSender,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier
	) {
		return (
			validatorSet,
			vertexStore,
			timeoutCalculator,
			safetyRules,
			initialViewUpdate,
			epoch
		) -> new Pacemaker(
			self,
			counters,
			validatorSet,
			vertexStore,
			safetyRules,
			timeout -> timeoutEventDispatcher.dispatch(new EpochLocalTimeoutOccurrence(epoch, timeout)),
			(scheduledTimeout, ms) -> localTimeoutSender.scheduleTimeout(Epoched.from(epoch, scheduledTimeout), ms),
			timeoutCalculator,
			nextCommandGenerator,
			proposalBroadcaster,
			hasher,
			voteDispatcher,
			timeSupplier,
			initialViewUpdate
		);
	}

	@Provides
	private BFTSyncRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		SyncVerticesResponseSender syncVerticesResponseSender
	) {
		return vertexStore -> new VertexStoreBFTSyncRequestProcessor(vertexStore, syncVerticesResponseSender);
	}

	@Provides
	private BFTSyncFactory bftSyncFactory(
		RemoteEventDispatcher<GetVerticesRequest> requestSender,
		@Self BFTNode self,
		@GetVerticesRequestRateLimit RateLimiter syncRequestRateLimiter,
		EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
		ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		SystemCounters counters
	) {
		return (vertexStore, pacemakerState, configuration) -> new BFTSync(
			self,
			syncRequestRateLimiter,
			vertexStore,
			pacemakerState,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			requestSender,
			syncLedgerRequestSender,
			timeoutDispatcher,
			configuration.getVertexStoreState().getRootHeader(),
			random,
			bftSyncPatienceMillis,
			counters
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		EventDispatcher<BFTInsertUpdate> updateSender,
		EventDispatcher<BFTRebuildUpdate> rebuildUpdateDispatcher,
		EventDispatcher<BFTHighQCUpdate> highQCUpdateEventDispatcher,
		EventDispatcher<BFTCommittedUpdate> committedDispatcher,
		Ledger ledger
	) {
		return vertexStoreState -> VertexStore.create(
			vertexStoreState,
			ledger,
			updateSender,
			rebuildUpdateDispatcher,
			highQCUpdateEventDispatcher,
			committedDispatcher
		);
	}
}
