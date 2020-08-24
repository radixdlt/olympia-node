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
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Vertex;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.bft.VertexStore.SyncedVertexSender;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.VertexSyncRx;
import com.radixdlt.consensus.epoch.LocalTimeout;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.sync.SyncRequestSender;
import com.radixdlt.utils.ScheduledSenderToRx;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.Hash;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.utils.SenderToRx;
import com.radixdlt.utils.ThreadFactories;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Module responsible for running BFT validator logic
 */
public final class ConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;
	private final int pacemakerTimeout;

	public ConsensusModule(int pacemakerTimeout) {
		this.pacemakerTimeout = pacemakerTimeout;
	}

	@Override
	protected void configure() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		ScheduledSenderToRx<LocalTimeout> localTimeouts = new ScheduledSenderToRx<>(ses);
		// Timed local messages
		bind(PacemakerRx.class).toInstance(localTimeouts::messages);
		bind(LocalTimeoutSender.class).toInstance(localTimeouts::scheduleSend);

		// Local messages
		SenderToRx<Vertex, Hash> syncedVertices = new SenderToRx<>(Vertex::getId);
		bind(VertexSyncRx.class).toInstance(syncedVertices::rx);
		bind(SyncedVertexSender.class).toInstance(syncedVertices::send);

		bind(EpochManager.class).in(Scopes.SINGLETON);
		bind(ConsensusRunner.class).in(Scopes.SINGLETON);
	}

	// TODO: Change Factory -> Provider
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
		return timeoutSender -> new FixedTimeoutPacemaker(pacemakerTimeout, timeoutSender);
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
		return (genesisVertex, genesisQC, syncedRadixEngine) -> new VertexStore(
			genesisVertex,
			genesisQC,
			syncedRadixEngine,
			syncVerticesRPCSender,
			syncedVertexSender,
			vertexStoreEventSender,
			syncRequestSender,
			counters
		);
	}
}
