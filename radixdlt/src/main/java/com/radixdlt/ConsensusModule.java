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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.VertexStoreSyncFactory;
import com.radixdlt.consensus.VertexStoreSyncVerticesRequestProcessorFactory;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.sync.VertexStoreSync;
import com.radixdlt.consensus.sync.VertexStoreSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.VertexStoreSyncVerticesRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreSyncVerticesRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.TimeSupplier;
import java.util.Comparator;

/**
 * Module responsible for running BFT validator logic
 */
public final class ConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;
	private final int pacemakerTimeout;

	public ConsensusModule(int pacemakerTimeout) {
		this.pacemakerTimeout = pacemakerTimeout;
	}

	@Provides
	private BFTFactory bftFactory(
		BFTEventSender bftEventSender,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		HashSigner signer,
		HashVerifier verifier,
		TimeSupplier timeSupplier,
		SystemCounters counters
	) {
		return (
			self,
			endOfEpochSender,
			pacemaker,
			vertexStore,
			vertexStoreSync,
			proposerElection,
			validatorSet,
			bftInfoSender
		) ->
			BFTBuilder.create()
				.self(self)
				.eventSender(bftEventSender)
				.nextCommandGenerator(nextCommandGenerator)
				.hasher(hasher)
				.signer(signer)
				.verifier(verifier)
				.counters(counters)
				.infoSender(bftInfoSender)
				.endOfEpochSender(endOfEpochSender)
				.pacemaker(pacemaker)
				.vertexStore(vertexStore)
				.vertexStoreSync(vertexStoreSync)
				.proposerElection(proposerElection)
				.validatorSet(validatorSet)
				.timeSupplier(timeSupplier)
				.build();
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
	private VertexStoreSyncVerticesRequestProcessorFactory vertexStoreSyncVerticesRequestProcessorFactory(
		SyncVerticesResponseSender syncVerticesResponseSender
	) {
		return vertexStore -> new VertexStoreSyncVerticesRequestProcessor(vertexStore, syncVerticesResponseSender);
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
		SyncedVertexSender syncedVertexSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC, ledger) -> new VertexStore(
			genesisVertex,
			genesisQC,
			ledger,
			syncedVertexSender,
			vertexStoreEventSender,
			counters
		);
	}
}
