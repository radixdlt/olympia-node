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
import com.radixdlt.api.InfoRx;
import com.radixdlt.api.LedgerRx;
import com.radixdlt.api.SubmissionErrorsRx;
import com.radixdlt.consensus.HashVerifier;
import com.radixdlt.consensus.bft.BFTBuilder;
import com.radixdlt.consensus.bft.BFTEventReducer.BFTEventSender;
import com.radixdlt.consensus.AddressBookValidatorSetProvider;
import com.radixdlt.consensus.BFTFactory;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.ConsensusRunner;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.epoch.EpochManager;
import com.radixdlt.consensus.ConsensusEventsRx;
import com.radixdlt.consensus.SyncEpochsRPCRx;
import com.radixdlt.consensus.epoch.EpochManager.EpochInfoSender;
import com.radixdlt.consensus.epoch.EpochManager.SyncEpochsRPCSender;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.mempool.SubmissionControl;
import com.radixdlt.mempool.SubmissionControlImpl;
import com.radixdlt.mempool.SubmissionControlImpl.SubmissionControlSender;
import com.radixdlt.api.InMemoryInfoStateManager;
import com.radixdlt.middleware2.InternalMessagePasser;
import com.radixdlt.consensus.HashSigner;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.bft.VertexStore;
import com.radixdlt.consensus.bft.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.bft.VertexStore.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.LocalTimeoutSender;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.StateSyncNetwork;
import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.consensus.sync.SyncedRadixEngine.SyncedRadixEngineEventSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.ECPublicKey;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.converters.AtomToClientAtomConverter;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralValidatorSync;
import com.radixdlt.middleware2.store.CommittedAtomsStore;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.network.addressbook.AddressBook;
import com.radixdlt.network.messaging.MessageCentral;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.universe.Universe;
import com.radixdlt.utils.ThreadFactories;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CerberusModule extends AbstractModule {
	private static final int DEFAULT_VERTEX_BUFFER_SIZE = 16;
	private static final long DEFAULT_VERTEX_UPDATE_FREQ = 1_000L;
	private final RuntimeProperties runtimeProperties;

	public CerberusModule(RuntimeProperties runtimeProperties) {
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
		bind(SubmissionControlSender.class).to(InternalMessagePasser.class);
		bind(SubmissionErrorsRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventsRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeSender.class).to(InternalMessagePasser.class);
		bind(EpochInfoSender.class).to(InternalMessagePasser.class);
		bind(InfoRx.class).to(InternalMessagePasser.class);
		bind(SyncedRadixEngineEventSender.class).to(InternalMessagePasser.class);
		bind(LedgerRx.class).to(InternalMessagePasser.class);
		bind(SyncedStateComputer.class).to(SyncedRadixEngine.class);

		// Network Sync messages
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
	SubmissionControl submissionControl(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		Serialization serialization,
		AtomToClientAtomConverter converter,
		SubmissionControlSender submissionControlSender
	) {
		return new SubmissionControlImpl(
			mempool,
			radixEngine,
			serialization,
			converter,
			submissionControlSender
		);
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
		Mempool mempool,
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
				.mempool(mempool)
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
	private InMemoryInfoStateManager infoStateRunner(InfoRx infoRx) {
		final int vertexBufferSize = runtimeProperties.get("api.debug.vertex_buffer_size", DEFAULT_VERTEX_BUFFER_SIZE);
		final long vertexUpdateFrequency = runtimeProperties.get("api.debug.vertex_update_freq", DEFAULT_VERTEX_UPDATE_FREQ);
		return new InMemoryInfoStateManager(infoRx, vertexBufferSize, vertexUpdateFrequency);
	}

	@Provides
	@Singleton
	private EpochManager epochManager(
		@Named("self") BFTNode self,
		SyncedRadixEngine syncedRadixEngine,
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
			syncedRadixEngine,
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
	private AddressBookValidatorSetProvider addressBookValidatorSetProvider(
		AddressBook addressBook,
		@Named("self") ECKeyPair selfKey
	) {
		final int fixedNodeCount = runtimeProperties.get("consensus.fixed_node_count", 1);

		return new AddressBookValidatorSetProvider(
			selfKey.getPublicKey(),
			addressBook,
			fixedNodeCount,
			(epoch, validators) -> {
				/*
				Builder<BFTValidator> validatorSetBuilder = ImmutableList.builder();
				Random random = new Random(epoch);
				List<Integer> indices = IntStream.range(0, validators.size()).boxed().collect(Collectors.toList());
				// Temporary mechanism to get some deterministic random set of validators
				for (long i = 0; i < epoch; i++) {
					random.nextInt(validators.size());
				}
				int randInt = random.nextInt(validators.size());
				int validatorSetSize = randInt + 1;

				for (int i = 0; i < validatorSetSize; i++) {
					int index = indices.remove(random.nextInt(indices.size()));
					BFTValidator validator = validators.get(index);
					validatorSetBuilder.add(validator);
				}

				ImmutableList<BFTValidator> validatorList = validatorSetBuilder.build();

				return BFTValidatorSet.from(validatorList);
				*/
				return BFTValidatorSet.from(validators);
			}
		);
	}

	@Provides
	@Singleton
	private SyncedRadixEngine syncedRadixEngine(
		Mempool mempool,
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		SyncedRadixEngineEventSender syncedRadixEngineEventSender,
		AddressBookValidatorSetProvider validatorSetProvider,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork,
		SystemCounters counters
	) {
		final long viewsPerEpoch = runtimeProperties.get("epochs.views_per_epoch", 100L);
		return new SyncedRadixEngine(
			mempool,
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			syncedRadixEngineEventSender,
			validatorSetProvider::getValidatorSet,
			View.of(viewsPerEpoch),
			addressBook,
			stateSyncNetwork,
			counters
		);
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
