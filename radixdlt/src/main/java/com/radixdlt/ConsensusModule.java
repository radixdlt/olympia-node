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
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.radixdlt.consensus.BFTConfiguration;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.Ledger;
import com.radixdlt.consensus.LedgerHeader;
import com.radixdlt.consensus.QuorumCertificate;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTCommittedUpdate;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.PacemakerInfoSender;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.BFTSyncRequestProcessor;
import com.radixdlt.consensus.bft.NewViewSigner;
import com.radixdlt.consensus.bft.SignedNewViewToLeaderSender.BFTNewViewSender;
import com.radixdlt.consensus.bft.VertexStore.BFTUpdateSender;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker;
import com.radixdlt.consensus.liveness.ExponentialTimeoutPacemaker.ProceedToViewSender;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.liveness.Pacemaker;
import com.radixdlt.consensus.liveness.ProposerElection;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.BFTSync.BFTSyncTimeoutScheduler;
import com.radixdlt.consensus.sync.BFTSync.SyncVerticesRequestSender;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor;
import com.radixdlt.consensus.sync.VertexStoreBFTSyncRequestProcessor.SyncVerticesResponseSender;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.sync.SyncLedgerRequestSender;
import com.radixdlt.consensus.liveness.PacemakerTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCounters.CounterType;
import com.radixdlt.network.TimeSupplier;
import java.util.Comparator;
import java.util.Random;
import java.util.Set;

/**
 * Module responsible for running BFT validator logic
 */
public final class ConsensusModule extends AbstractModule {
	private static final int ROTATING_WEIGHTED_LEADERS_CACHE_SIZE = 10;
	private final long pacemakerTimeout;
	private final double pacemakerRate;
	private final int pacemakerMaxExponent;

	public ConsensusModule(long pacemakerTimeout, double pacemakerRate, int pacemakerMaxExponent) {
		this.pacemakerTimeout = pacemakerTimeout;
		this.pacemakerRate = pacemakerRate;
		this.pacemakerMaxExponent = pacemakerMaxExponent;
	}

	@Override
	public void configure() {
		Multibinder.newSetBinder(binder(), VertexStoreEventSender.class);
	}

	@Provides
	private VertexStoreEventSender sender(Set<VertexStoreEventSender> senders) {
		return new VertexStoreEventSender() {
			@Override
			public void sendCommitted(BFTCommittedUpdate committedUpdate) {
				senders.forEach(s -> s.sendCommitted(committedUpdate));
			}

			@Override
			public void highQC(QuorumCertificate qc) {
				senders.forEach(s -> s.highQC(qc));
			}
		};
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
			pacemaker,
			vertexStore,
			vertexStoreSync,
			proposerElection,
			validatorSet
		) ->
			BFTBuilder.create()
				.self(self)
				.eventSender(bftEventSender)
				.nextCommandGenerator(nextCommandGenerator)
				.hasher(hasher)
				.signer(signer)
				.verifier(verifier)
				.counters(counters)
				.pacemaker(pacemaker)
				.vertexStore(vertexStore)
				.bftSyncer(vertexStoreSync)
				.proposerElection(proposerElection)
				.validatorSet(validatorSet)
				.timeSupplier(timeSupplier)
				.build();
	}

	@Provides
	@Singleton
	public BFTEventProcessor eventProcessor(
		@Named("self") BFTNode self,
		BFTConfiguration config,
		BFTFactory bftFactory,
		Pacemaker pacemaker,
		VertexStore vertexStore,
		BFTSync vertexStoreSync,
		ProposerElection proposerElection
	) {
		return bftFactory.create(
			self,
			pacemaker,
			vertexStore,
			vertexStoreSync,
			proposerElection,
			config.getValidatorSet()
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
	ProceedToViewSender proceedToViewSender(
		NewViewSigner newViewSigner,
		ProposerElection proposerElection,
		BFTNewViewSender bftNewViewSender
	) {
		return new SignedNewViewToLeaderSender(
			newViewSigner,
			proposerElection,
			bftNewViewSender
		);
	}

	@Provides
	@Singleton
	private Pacemaker pacemaker(ProceedToViewSender proceedToViewSender, PacemakerTimeoutSender timeoutSender, PacemakerInfoSender infoSender) {
		return new ExponentialTimeoutPacemaker(
			this.pacemakerTimeout, this.pacemakerRate, this.pacemakerMaxExponent, proceedToViewSender, timeoutSender, infoSender
		);
	}

	@Provides
	private BFTSyncRequestProcessor bftSyncRequestProcessor(
		VertexStore vertexStore,
		SyncVerticesResponseSender responseSender
	) {
		return new VertexStoreBFTSyncRequestProcessor(vertexStore, responseSender);
	}

	@Provides
	@Singleton
	private BFTSync bftSync(
		VertexStore vertexStore,
		Pacemaker pacemaker,
		SyncVerticesRequestSender requestSender,
		SyncLedgerRequestSender syncLedgerRequestSender,
		BFTSyncTimeoutScheduler timeoutScheduler,
		BFTConfiguration configuration,
		SystemCounters counters,
		Random random,
		@BFTSyncPatienceMillis int bftSyncPatienceMillis
	) {
		return new BFTSync(
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
	@Singleton
	private VertexStore vertexStore(
		VertexStoreEventSender vertexStoreEventSender,
		BFTUpdateSender updateSender,
		BFTConfiguration bftConfiguration,
		SystemCounters counters,
		Ledger ledger
	) {
		return new VertexStore(
			bftConfiguration.getGenesisVertex(),
			bftConfiguration.getGenesisQC(),
			ledger,
			updateSender,
			vertexStoreEventSender,
			counters
		);
	}
}
