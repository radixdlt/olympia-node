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

package com.radixdlt.integration.distributed.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.network.TimeSupplier;
import java.util.Comparator;

/**
 * Module responsible for running BFT validator logic
 */
public final class DeterministicConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;

	// An arbitrary timeout for the pacemaker, as time is handled differently
	// in a deterministic test.
	private static final long ARBITRARY_PACEMAKER_TIMEOUT_MS = 5000;

	public DeterministicConsensusModule() {
		// Nothing here right now
	}

	@Override
	protected void configure() {
		bind(EpochManager.class).in(Scopes.SINGLETON);
	}

	@Provides
	@Singleton
	private BFTFactory bftFactory(
		@Named("self") BFTNode self,
		BFTEventSender bftEventSender,
		NextCommandGenerator nextCommandGenerator,
		Hasher hasher,
		HashSigner signer,
		HashVerifier verifier,
		TimeSupplier timeSupplier,
		SystemCounters counters
	) {
		return (
			endOfEpochSender,
			pacemaker,
			vertexStore,
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
				.proposerElection(proposerElection)
				.validatorSet(validatorSet)
				.timeSupplier(timeSupplier)
				.build();
	}

	@Provides
	@Singleton
	private ProposerElectionFactory proposerElectionFactory() {
		return validatorSet -> new WeightedRotatingLeaders(
			validatorSet,
			Comparator.comparing(v -> v.getNode().getKey().euid()),
			ROTATING_WEIGHTED_LEADERS_CACHE_SIZE
		);
	}

	@Provides
	@Singleton
	private PacemakerFactory pacemakerFactory() {
		return timeoutSender -> new FixedTimeoutPacemaker(ARBITRARY_PACEMAKER_TIMEOUT_MS, timeoutSender);
	}

	@Provides
	@Singleton
	private VertexStoreFactory vertexStoreFactory(
		SyncVerticesRPCSender syncVerticesRPCSender,
		VertexStoreEventSender vertexStoreEventSender,
		SyncedVertexSender syncedVertexSender,
		SyncRequestSender syncRequestSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC, ledger) -> new VertexStore(
			genesisVertex,
			genesisQC,
			ledger,
			syncVerticesRPCSender,
			syncedVertexSender,
			vertexStoreEventSender,
			syncRequestSender,
			counters
		);
	}
}
