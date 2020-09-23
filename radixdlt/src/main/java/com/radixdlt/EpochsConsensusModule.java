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
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Timeout;
import com.radixdlt.consensus.VerifiedLedgerHeaderAndProof;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.VertexStoreSyncFactory;
import com.radixdlt.consensus.BFTSyncRequestProcessorFactory;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.epoch.EpochChange;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochView;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.sync.VertexStoreSync;
import com.radixdlt.consensus.sync.VertexStoreSync.SyncVerticesRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.TimeSupplier;
import java.util.Comparator;

public class EpochsConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;
	private final int pacemakerTimeout;

	public EpochsConsensusModule(int pacemakerTimeout) {
		this.pacemakerTimeout = pacemakerTimeout;
	}

	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
	}

	@Provides
	private TimeoutSender initialTimeoutSender(LocalTimeoutSender localTimeoutSender, EpochChange initialEpoch) {
		return (view, ms) -> localTimeoutSender.scheduleTimeout(new LocalTimeout(initialEpoch.getEpoch() , view), ms);
	}

	@Provides
	private BFTInfoSender initialInfoSender(EpochInfoSender epochInfoSender, EpochChange initialEpoch) {
		return new BFTInfoSender() {
			@Override
			public void sendCurrentView(View view) {
				epochInfoSender.sendCurrentView(EpochView.of(initialEpoch.getEpoch(), view));
			}

			@Override
			public void sendTimeoutProcessed(View view, BFTNode leader) {
				Timeout timeout = new Timeout(EpochView.of(initialEpoch.getEpoch(), view), leader);
				epochInfoSender.sendTimeoutProcessed(timeout);
			}
		};
	}

	// TODO: Load from storage
	@Provides
	private EpochChange initialEpoch(
		VerifiedLedgerHeaderAndProof proof,
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
	private PacemakerFactory pacemakerFactory() {
		return timeoutSender -> new FixedTimeoutPacemaker(pacemakerTimeout, timeoutSender);
	}

	@Provides
	private BFTSyncRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		SyncVerticesResponseSender syncVerticesResponseSender
	) {
		return vertexStore -> new VertexStoreBFTSyncRequestProcessor(vertexStore, syncVerticesResponseSender);
	}

	@Provides
	private VertexStoreSyncFactory vertexStoreSyncFactory(
		SyncVerticesRequestSender requestSender,
		SyncLedgerRequestSender syncLedgerRequestSender,
		Ledger ledger

	) {
		return vertexStore -> new VertexStoreSync(
			vertexStore,
			Comparator.comparingLong((LedgerHeader h) -> h.getAccumulatorState().getStateVersion()),
			requestSender,
			syncLedgerRequestSender,
			ledger
		);
	}

	@Provides
	private VertexStoreFactory vertexStoreFactory(
		VertexStoreEventSender vertexStoreEventSender,
		BFTUpdateSender updateSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC, ledger) -> new VertexStore(
			genesisVertex,
			genesisQC,
			ledger,
			updateSender,
			vertexStoreEventSender,
			counters
		);
	}
}

