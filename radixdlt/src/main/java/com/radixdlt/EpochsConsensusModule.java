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
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.ProposerElectionFactory;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.epoch.VertexStoreFactory;
import com.radixdlt.consensus.epoch.BFTSyncFactory;
import com.radixdlt.consensus.epoch.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemakerFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.ProceedToViewSender;
import com.radixdlt.consensus.liveness.ProposalBroadcaster;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.crypto.Hasher;

import com.radixdlt.store.LastEpochProof;
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
	private PacemakerTimeoutSender initialTimeoutSender(LocalTimeoutSender localTimeoutSender, EpochChange initialEpoch) {
		return (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(initialEpoch.getEpoch(), view), ms);
	}

	@Provides
	private PacemakerInfoSender initialInfoSender(EpochInfoSender epochInfoSender, EpochChange initialEpoch, ProposerElection proposerElection) {
		return new PacemakerInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochInfoSender.sendCurrentView(EpochView.of(initialEpoch.getEpoch(), view));
			}

			@Override
			public void sendTimeoutProcessed(View view) {
				BFTNode leader = proposerElection.getProposer(view);
				Timeout timeout = new Timeout(EpochView.of(initialEpoch.getEpoch(), view), leader);
				epochInfoSender.sendTimeoutProcessed(timeout);
			}
		};
	}

	// TODO: Load from storage
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
	private PacemakerFactory pacemakerFactory(
		@Self BFTNode self,
		SystemCounters counters,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		HashSigner signer,
		ProposalBroadcaster proposalBroadcaster,
		ProceedToViewSender proceedToViewSender,
		@PacemakerTimeout long pacemakerTimeout,
		@PacemakerRate double pacemakerRate,
		@PacemakerMaxExponent int pacemakerMaxExponent
	) {
		return new ExponentialTimeoutPacemakerFactory(
			pacemakerTimeout,
			pacemakerRate,
			pacemakerMaxExponent,
			self,
			counters,
			nextCommandGenerator,
			hasher,
			signer,
			proposalBroadcaster,
			proceedToViewSender
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
		SyncVerticesRequestSender requestSender,
		SyncLedgerRequestSender syncLedgerRequestSender,
		BFTSyncTimeoutScheduler timeoutScheduler,
		BFTConfiguration configuration,
		SystemCounters counters,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis
	) {
		return (vertexStore, pacemaker) -> new BFTSync(
			vertexStore,
			pacemaker,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			(node, request)  -> {
				counters.increment(CounterType.BFT_SYNC_REQUESTS_SENT);
				requestSender.sendGetVerticesRequest(node, request);
			},
			syncLedgerRequestSender,
			timeoutScheduler,
			configuration.getGenesisHeader(),
			random,
			bftSyncPatienceMillis
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		BFTUpdateSender updateSender,
		SystemCounters counters,
		Ledger ledger,
		VertexStoreEventSender vertexStoreEventSender
	) {
		return (genesisVertex, genesisQC) -> VertexStore.create(
			genesisVertex,
			genesisQC,
			ledger,
			updateSender,
			vertexStoreEventSender,
			counters
		);
	}
}

