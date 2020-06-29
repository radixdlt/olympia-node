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
import com.radixdlt.consensus.BFTEventSender;
import com.radixdlt.consensus.AddressBookValidatorSetProvider;
import com.radixdlt.consensus.CommittedStateSyncRx;
import com.radixdlt.consensus.DefaultHasher;
import com.radixdlt.consensus.EpochChangeRx;
import com.radixdlt.consensus.EventCoordinatorNetworkRx;
import com.radixdlt.consensus.SyncedStateComputer;
import com.radixdlt.consensus.VertexStoreEventsRx;
import com.radixdlt.consensus.InternalMessagePasser;
import com.radixdlt.consensus.ProposerElectionFactory;
import com.radixdlt.consensus.Hasher;
import com.radixdlt.consensus.SyncVerticesRPCRx;
import com.radixdlt.consensus.VertexStore;
import com.radixdlt.consensus.VertexStore.VertexStoreEventSender;
import com.radixdlt.consensus.SyncVerticesRPCSender;
import com.radixdlt.consensus.VertexStoreFactory;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker.TimeoutSender;
import com.radixdlt.consensus.liveness.FixedTimeoutPacemaker;
import com.radixdlt.consensus.liveness.PacemakerFactory;
import com.radixdlt.consensus.liveness.PacemakerRx;
import com.radixdlt.consensus.liveness.ScheduledTimeoutSender;
import com.radixdlt.consensus.liveness.WeightedRotatingLeaders;
import com.radixdlt.consensus.sync.StateSyncNetwork;
import com.radixdlt.consensus.sync.SyncedRadixEngine;
import com.radixdlt.consensus.sync.SyncedRadixEngine.CommittedStateSyncSender;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware2.LedgerAtom;
import com.radixdlt.middleware2.network.MessageCentralBFTNetwork;
import com.radixdlt.middleware2.network.MessageCentralSyncVerticesRPCNetwork;
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
		// dependencies
		bind(TimeoutSender.class).to(ScheduledTimeoutSender.class);
		bind(PacemakerRx.class).to(ScheduledTimeoutSender.class);

		bind(VertexStoreEventsRx.class).to(InternalMessagePasser.class);
		bind(VertexStoreEventSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncSender.class).to(InternalMessagePasser.class);
		bind(CommittedStateSyncRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeRx.class).to(InternalMessagePasser.class);
		bind(EpochChangeSender.class).to(InternalMessagePasser.class);
		bind(SyncedStateComputer.class).to(SyncedRadixEngine.class);

		bind(SyncVerticesRPCSender.class).to(MessageCentralSyncVerticesRPCNetwork.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralSyncVerticesRPCNetwork.class);
		bind(MessageCentralBFTNetwork.class).in(Scopes.SINGLETON);
		bind(BFTEventSender.class).to(MessageCentralBFTNetwork.class);
		bind(EventCoordinatorNetworkRx.class).to(MessageCentralBFTNetwork.class);
		bind(MessageCentralSyncVerticesRPCNetwork.class).in(Scopes.SINGLETON);
		bind(SyncVerticesRPCSender.class).to(MessageCentralSyncVerticesRPCNetwork.class);
		bind(SyncVerticesRPCRx.class).to(MessageCentralSyncVerticesRPCNetwork.class);

		bind(Hasher.class).to(DefaultHasher.class);
	}

	@Provides
	@Singleton
	private InternalMessagePasser internalMessagePasser() {
		return new InternalMessagePasser();
	}

	@Provides
	@Singleton
	private SyncedRadixEngine syncedRadixEngine(
		RadixEngine<LedgerAtom> radixEngine,
		CommittedAtomsStore committedAtomsStore,
		CommittedStateSyncSender committedStateSyncSender,
		EpochChangeSender epochChangeSender,
		AddressBook addressBook,
		StateSyncNetwork stateSyncNetwork,
		@Named("self") ECKeyPair selfKey
	) {
		final int fixedNodeCount = runtimeProperties.get("consensus.fixed_node_count", 1);
		AddressBookValidatorSetProvider validatorSetProvider = new AddressBookValidatorSetProvider(
			selfKey.getPublicKey(),
			addressBook,
			fixedNodeCount
		);

		return new SyncedRadixEngine(
			radixEngine,
			committedAtomsStore,
			committedStateSyncSender,
			epochChangeSender,
			validatorSetProvider::getValidatorSet,
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
	private PacemakerFactory pacemakerFactory(
		TimeoutSender timeoutSender
	) {
		final int pacemakerTimeout = runtimeProperties.get("consensus.pacemaker_timeout_millis", 5000);
		return () -> new FixedTimeoutPacemaker(pacemakerTimeout, timeoutSender);
	}

	@Provides
	@Singleton
	private VertexStoreFactory vertexStoreFactory(
		SyncedRadixEngine syncedRadixEngine,
		SyncVerticesRPCSender syncVerticesRPCSender,
		VertexStoreEventSender vertexStoreEventSender,
		SystemCounters counters
	) {
		return (genesisVertex, genesisQC) -> new VertexStore(
			genesisVertex,
			genesisQC,
			syncedRadixEngine,
			syncVerticesRPCSender,
			vertexStoreEventSender,
			counters
		);
	}
}
