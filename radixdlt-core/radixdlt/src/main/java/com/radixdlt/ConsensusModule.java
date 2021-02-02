/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.Vote;
import com.radixdlt.consensus.bft.BFTHighQCUpdate;
import com.radixdlt.consensus.bft.BFTRebuildUpdate;
import com.radixdlt.consensus.bft.BFTInsertUpdate;
import com.radixdlt.consensus.bft.NoVote;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.ViewQuorumReached;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.LocalTimeoutOccurrence;
import com.radixdlt.consensus.liveness.PacemakerReducer;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.liveness.ExponentialPacemakerTimeoutCalculator;
import com.radixdlt.consensus.liveness.PacemakerState;
import com.radixdlt.consensus.liveness.PacemakerTimeoutCalculator;
import com.radixdlt.consensus.sync.GetVerticesRequest;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.RemoteEventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.LocalSyncRequest;
import java.util.Comparator;
import java.util.Random;

/**
 * Module responsible for running BFT validator logic
 */
public final class ConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;

	@Override
	public void configure() {
		bind(SafetyRules.class).in(Scopes.SINGLETON);
		bind(PacemakerState.class).in(Scopes.SINGLETON);
		bind(PacemakerReducer.class).to(PacemakerState.class);
		bind(ExponentialPacemakerTimeoutCalculator.class).in(Scopes.SINGLETON);
		bind(PacemakerTimeoutCalculator.class).to(ExponentialPacemakerTimeoutCalculator.class);
	}

	@Provides
	private BFTFactory bftFactory(
		Hasher hasher,
		HashVerifier verifier,
		EventDispatcher<ViewQuorumReached> viewQuorumReachedEventDispatcher,
		EventDispatcher<NoVote> noVoteEventDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher
	) {
		return (
			self,
			pacemaker,
			vertexStore,
			bftSyncer,
			viewQuorumReachedEventProcessor,
			validatorSet,
			viewUpdate,
			safetyRules
		) ->
			BFTBuilder.create()
				.self(self)
				.hasher(hasher)
				.verifier(verifier)
				.voteDispatcher(voteDispatcher)
				.safetyRules(safetyRules)
				.pacemaker(pacemaker)
				.vertexStore(vertexStore)
				.viewQuorumReachedEventDispatcher(viewQuorumReached -> {
					// FIXME: a hack for now until replacement of epochmanager factories
					viewQuorumReachedEventProcessor.process(viewQuorumReached);
					viewQuorumReachedEventDispatcher.dispatch(viewQuorumReached);
				})
				.noVoteEventDispatcher(noVoteEventDispatcher)
				.viewUpdate(viewUpdate)
				.bftSyncer(bftSyncer)
				.validatorSet(validatorSet)
				.build();
	}

	@ProvidesIntoSet
	public EventProcessor<BFTRebuildUpdate> bftRebuildUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return eventProcessor::processBFTRebuildUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTInsertUpdate> bftUpdateEventProcessor(BFTEventProcessor eventProcessor) {
		return eventProcessor::processBFTUpdate;
	}

	@ProvidesIntoSet
	public EventProcessor<BFTInsertUpdate> bftSync(BFTSync bftSync) {
		return bftSync::processBFTUpdate;
	}

	@Provides
	@Singleton
	public BFTEventProcessor eventProcessor(
		@Self BFTNode self,
		BFTConfiguration config,
		BFTFactory bftFactory,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		BFTSync bftSync,
		SafetyRules safetyRules,
		ViewUpdate viewUpdate
	) {
		return bftFactory.create(
			self,
			pacemaker,
			vertexStore,
			bftSync,
			bftSync.viewQuorumReachedEventProcessor(),
			config.getValidatorSet(),
			viewUpdate,
			safetyRules
		);
	}

	@Provides
	private ProposerElection proposerElection(BFTConfiguration configuration) {
		return new WeightedRotatingLeaders(
			configuration.getValidatorSet(),
			Comparator.comparing(v -> v.getNode().getKey().euid()),
			ROTATING_WEIGHTED_LEADERS_CACHE_SIZE
		);
	}

	@Provides
	@Singleton
	private Pacemaker pacemaker(
		@Self BFTNode self,
		SafetyRules safetyRules,
		SystemCounters counters,
		BFTConfiguration configuration,
		VertexStore vertexStore,
		EventDispatcher<LocalTimeoutOccurrence> timeoutDispatcher,
		ScheduledEventDispatcher<ScheduledLocalTimeout> timeoutSender,
		PacemakerTimeoutCalculator timeoutCalculator,
		NextCommandGenerator nextCommandGenerator,
		ProposalBroadcaster proposalBroadcaster,
		Hasher hasher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier,
		ViewUpdate initialViewUpdate
	) {
		BFTValidatorSet validatorSet = configuration.getValidatorSet();
		return new Pacemaker(
			self,
			counters,
			validatorSet,
			vertexStore,
			safetyRules,
			timeoutDispatcher,
			timeoutSender,
			timeoutCalculator,
			nextCommandGenerator,
			proposalBroadcaster,
			hasher,
			voteDispatcher,
			timeSupplier,
			initialViewUpdate
		);
	}

	@ProvidesIntoSet
	private RemoteEventProcessor<GetVerticesRequest> bftSyncRequestProcessor(
		VertexStore vertexStore,
		SyncVerticesResponseSender responseSender
	) {
		return new VertexStoreBFTSyncRequestProcessor(vertexStore, responseSender);
	}

	@Provides
	@Singleton
	private BFTSync bftSync(
		@Self BFTNode self,
		@GetVerticesRequestRateLimit RateLimiter syncRequestRateLimiter,
		VertexStore vertexStore,
		PacemakerReducer pacemakerReducer,
		RemoteEventDispatcher<GetVerticesRequest> requestSender,
		EventDispatcher<LocalSyncRequest> syncLedgerRequestSender,
		ScheduledEventDispatcher<VertexRequestTimeout> timeoutDispatcher,
		@LastProof VerifiedLedgerHeaderAndProof ledgerLastProof, // Use this instead of configuration.getRoot()
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		SystemCounters counters
	) {
		return new BFTSync(
			self,
			syncRequestRateLimiter,
			vertexStore,
			pacemakerReducer,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			requestSender,
			syncLedgerRequestSender,
			timeoutDispatcher,
			ledgerLastProof,
			random,
			bftSyncPatienceMillis,
			counters
		);
	}

	@Provides
	@Singleton
	private VertexStore vertexStore(
		EventDispatcher<BFTInsertUpdate> updateSender,
		EventDispatcher<BFTRebuildUpdate> rebuildUpdateDispatcher,
		EventDispatcher<BFTHighQCUpdate> highQCUpdateEventDispatcher,
		EventDispatcher<BFTCommittedUpdate> committedSender,
		BFTConfiguration bftConfiguration,
		Ledger ledger
	) {
		return VertexStore.create(
			bftConfiguration.getVertexStoreState(),
			ledger,
			updateSender,
			rebuildUpdateDispatcher,
			highQCUpdateEventDispatcher,
			committedSender
		);
	}
}
