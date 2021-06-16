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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.consensus.Proposal;
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
import com.radixdlt.consensus.liveness.NextTxnsGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventDispatcher;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.RemoteEventDispatcher;
import com.radixdlt.environment.ScheduledEventDispatcher;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.utils.TimeSupplier;
import com.radixdlt.store.LastProof;
import com.radixdlt.sync.messages.local.LocalSyncRequest;
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
		bind(VertexStoreBFTSyncRequestProcessor.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(ViewUpdate.class);
		eventBinder.addBinding().toInstance(BFTRebuildUpdate.class);
		eventBinder.addBinding().toInstance(BFTInsertUpdate.class);
		eventBinder.addBinding().toInstance(Proposal.class);
		eventBinder.addBinding().toInstance(Vote.class);
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
		NextTxnsGenerator nextTxnsGenerator,
		Hasher hasher,
		RemoteEventDispatcher<Proposal> proposalDispatcher,
		RemoteEventDispatcher<Vote> voteDispatcher,
		TimeSupplier timeSupplier,
		ViewUpdate initialViewUpdate,
        SystemCounters systemCounters
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
			nextTxnsGenerator,
			proposalDispatcher,
			voteDispatcher,
			hasher,
			timeSupplier,
			initialViewUpdate,
			systemCounters
		);
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
		@LastProof LedgerProof ledgerLastProof, // Use this instead of configuration.getRoot()
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis,
		Hasher hasher,
		SystemCounters counters
	) {
		return new BFTSync(
			self,
			syncRequestRateLimiter,
			vertexStore,
			hasher,
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
		Ledger ledger,
		Hasher hasher
	) {
		return VertexStore.create(
			bftConfiguration.getVertexStoreState(),
			ledger,
			hasher,
			updateSender,
			rebuildUpdateDispatcher,
			highQCUpdateEventDispatcher,
			committedSender
		);
	}
}
