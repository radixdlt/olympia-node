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
import com.radixdlt.consensus.BFTEventReducer;
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.AddressBookValidatorSetProvider;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.EpochManager;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.PendingVotes;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.SyncEpochsRPCSender;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.InternalMessagePasser;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.View;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.MempoolProposalGenerator;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ProposalGenerator;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.safety.SafetyRules;
import com.radixdlt.consensus.safety.SafetyState;
import com.radixdlt.consensus.sync.StateSyncNetwork;
import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.utils.ThreadFactories;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CerberusModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger("Startup");

	private final RuntimeProperties runtimeProperties;

	public CerberusModule(RuntimeProperties runtimeProperties) {
		this.runtimeProperties = Objects.requireNonNull(runtimeProperties);
	}

	@Override
	protected void configure() {
		// Timed local messages
		bind(PacemakerRx.class).to(ScheduledTimeoutSender.class);

		// Local messages
		bind(VertexStoreEventsRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeSender.class).to(InternalMessagePasser.class);

		bind(SyncedStateComputer.class).to(SyncedRadixEngine.class);

		// Sync messages
		bind(SyncEpochsRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncEpochsRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCSender.class).to(MessageCentralValidatorSync.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralValidatorSync.class);
		bind(MessageCentralValidatorSync.class).in(Scopes.SINGLETON);

		// BFT messages
		bind(BFTEventSender.class).to(MessageCentralBFTNetwork.class);
		bind(ConsensusEventsRx.class).to(MessageCentralBFTNetwork.class);
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);

		// Configuration
		bind(Hasher.class).to(DefaultHasher.class);
	}

	@Provides
	@Singleton
	private BFTFactory bftFactory(
		BFTEventSender bftEventSender,
		Mempool mempool,
		@Named("self") ECKeyPair selfKey,
		Hasher hasher,
		SystemCounters counters
	) {
		return (
			endOfEpochSender,
			pacemaker,
			vertexStore,
			proposerElection,
			validatorSet
		) -> {
			final ProposalGenerator proposalGenerator = new MempoolProposalGenerator(vertexStore, mempool);
			final SafetyRules safetyRules = new SafetyRules(selfKey, SafetyState.initialState(), hasher);
			final PendingVotes pendingVotes = new PendingVotes(hasher);

			return new BFTEventReducer(
				proposalGenerator,
				mempool,
				bftEventSender,
				endOfEpochSender,
				safetyRules,
				pacemaker,
				vertexStore,
				pendingVotes,
				proposerElection,
				selfKey,
				validatorSet,
				counters
			);
		};
	}

	@Provides
	@Singleton
	private EpochManager epochManager(
		SyncedRadixEngine syncedRadixEngine,
		BFTFactory bftFactory,
		SyncEpochsRPCSender syncEpochsRPCSender,
		ScheduledTimeoutSender scheduledTimeoutSender,
		PacemakerFactory pacemakerFactory,
		VertexStoreFactory vertexStoreFactory,
		ProposerElectionFactory proposerElectionFactory,
		@Named("self") ECKeyPair selfKey,
		SystemCounters counters
	) {
		return new EpochManager(
			selfKey.euid().toString().substring(0, 6),
			syncedRadixEngine,
			syncEpochsRPCSender,
			scheduledTimeoutSender,
			pacemakerFactory,
			vertexStoreFactory,
			proposerElectionFactory,
			bftFactory,
			selfKey.getPublicKey(),
			counters
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
	private AddressBookValidatorSetProvider addressBookValidatorSetProvider(
		AddressBook addressBook,
		@Named("self") ECKeyPair selfKey
	) {
		final int fixedNodeCount = runtimeProperties.get("consensus.fixed_node_count", 1);

		return new AddressBookValidatorSetProvider(
			selfKey.getPublicKey(),
			addressBook,
			fixedNodeCount
		);
	}

	@Provides
	@Singleton
	private SyncedRadixEngine syncedRadixEngine(
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		AddressBookValidatorSetProvider validatorSetProvider,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork
	) {
		final long viewsPerEpoch = runtimeProperties.get("epochs.views_per_epoch", 100L);
		return new SyncedRadixEngine(
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			validatorSetProvider::getValidatorSet,
			View.of(viewsPerEpoch),
			addressBook,
			stateSyncNetwork
		);
	}

	@Provides
	@Singleton
	private ProposerElectionFactory proposerElectionFactory() {
		final int cacheSize = runtimeProperties.get("consensus.weighted_rotating_leaders.cache_size", 10);
		return validatorSet -> new WeightedRotatingLeaders(validatorSet, Comparator.comparing(v -> v.nodeKey().euid()), cacheSize);
	}

	@Provides
	@Singleton
	private ScheduledTimeoutSender timeoutSender() {
		ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(ThreadFactories.daemonThreads("TimeoutSender"));
		return new ScheduledTimeoutSender(ses);
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
