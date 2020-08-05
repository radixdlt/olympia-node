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
import com.google.inject.name.Named;
import com.radixdlt.systeminfo.InfoRx;
import com.radixdlt.api.LedgerRx;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.liveness.NextCommandGenerator;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.middleware2.CommittedAtom;
import com.radixdlt.middleware2.InternalMessagePasser;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.ThreadFactories;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ConsensusModule extends AbstractModule {
	private final RuntimeProperties runtimeProperties;

	public ConsensusModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		// Configuration
		bind(Hasher.class).to(DefaultHasher.class);
		bind(HashVerifier.class).toInstance(ECPublicKey::verify);

		// Timed local messages
		bind(PacemakerRx.class).to(ScheduledLocalTimeoutSender.class);
		bind(LocalTimeoutSender.class).to(ScheduledLocalTimeoutSender.class);

		// Local messages
		bind(VertexStoreEventsRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeRx.class).to(InternalMessagePasser.class);
		bind(EpochInfoSender.class).to(InternalMessagePasser.class);
		bind(InfoRx.class).to(InternalMessagePasser.class);
		bind(LedgerRx.class).to(InternalMessagePasser.class);

		// Network BFT/Epoch Sync messages
		bind(SyncEpochsRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralValidatorSync.class);

		// Network BFT messages
		bind(BFTEventSender.class).to(MessageCentralBFTNetwork.class);
		bind(ConsensusEventsRx.class).to(MessageCentralBFTNetwork.class);
	}


	@Provides
	@Singleton
	HashSigner hashSigner(
		@Named("self") ECKeyPair selfKey
	) {
		return selfKey::sign;
	}

	@Provides
	@Singleton
	MessageCentralValidatorSync validatorSync(
		@Named("self") BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		return new MessageCentralValidatorSync(self, universe, addressBook, messageCentral);
	}

	@Provides
	@Singleton
	MessageCentralBFTNetwork bftNetwork(
		@Named("self") BFTNode self,
		Universe universe,
		AddressBook addressBook,
		MessageCentral messageCentral
	) {
		return new MessageCentralBFTNetwork(self, universe, addressBook, messageCentral);
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
	private EpochManager epochManager(
		@Named("self") BFTNode self,
		SyncedStateComputer<CommittedAtom> syncer,
		BFTFactory bftFactory,
		SyncEpochsRPCSender syncEpochsRPCSender,
		LocalTimeoutSender scheduledTimeoutSender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		ProposerElectionFactory proposerElectionFactory,
		SystemCounters counters,
		EpochInfoSender epochInfoSender
	) {
		return new EpochManager(
			self,
			syncer,
			syncEpochsRPCSender,
			scheduledTimeoutSender,
			pacemakerFactory,
			vertexStoreFactory,
			proposerElectionFactory,
			bftFactory,
			counters,
			epochInfoSender
		);
	}

	@Provides
	@Singleton
	private ConsensusRunner consensusRunner(
		EpochChangeRx epochChangeRx,
		ConsensusEventsRx networkRx,
		PacemakerRx pacemakerRx,
		VertexStoreEventsRx vertexStoreEventsRx,
		CommittedStateSyncRx committedStateSyncRx,
		SyncVerticesRPCRx rpcRx,
		SyncEpochsRPCRx epochsRPCRx,
		EpochManager epochManager
	) {
		return new ConsensusRunner(
			epochChangeRx,
			networkRx,
			pacemakerRx,
			vertexStoreEventsRx,
			committedStateSyncRx,
			rpcRx,
			epochsRPCRx,
			epochManager
		);
	}

	@Provides
	@Singleton
	private InternalMessagePasser internalMessagePasser() {
		return new InternalMessagePasser();
	}

	@Provides
	@Singleton
	private ProposerElectionFactory proposerElectionFactory() {
		final int cacheSize = runtimeProperties.get("consensus.weighted_rotating_leaders.cache_size", 10);
		return validatorSet -> new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.getNode().getKey().euid()), cacheSize);
	}

	@Provides
	@Singleton
	private ScheduledLocalTimeoutSender timeoutSender() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		return new ScheduledLocalTimeoutSender(ses);
	}

	@Provides
	@Singleton
	private PacemakerFactory pacemakerFactory() {
		final int pacemakerTimeout = runtimeProperties.get("consensus.pacemaker_timeout_millis", 5000);
		return timeoutSender -> new FixedTimeoutPacemaker(pacemakerTimeout, timeoutSender);
	}

	@Provides
	@Singleton
	private VertexStoreFactory vertexStoreFactory(
		SyncVerticesRPCSender syncVerticesRPCSender,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC, syncedRadixEngine) -> new VertexStore(
			genesisVertex,
			genesisQC,
			syncedRadixEngine,
			syncVerticesRPCSender,
			vertexStoreEventSender,
			counters
		);
	}
}
