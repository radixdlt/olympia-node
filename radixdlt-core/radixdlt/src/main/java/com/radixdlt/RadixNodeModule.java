/*
 * (C) Copyright 2021 Radix DLT Ltd
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

import com.radixdlt.statecomputer.forks.ForksModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.radix.universe.system.LocalSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.api.Rri;
import com.radixdlt.api.module.ArchiveApiModule;
import com.radixdlt.api.module.CommonApiModule;
import com.radixdlt.api.module.NodeApiModule;
import com.radixdlt.api.qualifier.Endpoints;
import com.radixdlt.api.service.RriParser;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.keys.PersistedBFTKeyModule;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.messaging.MessagingModule;
import com.radixdlt.network.p2p.P2PModule;
import com.radixdlt.network.p2p.PeerDiscoveryModule;
import com.radixdlt.network.p2p.PeerLivenessMonitorModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputerModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.forks.BetanetForkConfigsModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesFromPropertiesModule;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.universe.UniverseModule;

import java.io.IOException;
import java.util.List;

import static com.radixdlt.EndpointConfig.enabledArchiveEndpoints;
import static com.radixdlt.EndpointConfig.enabledNodeEndpoints;
import static com.radixdlt.EndpointConfig.endpointStatuses;

/**
 * Module which manages everything in a single node
 */
public final class RadixNodeModule extends AbstractModule {
	private static final Logger log = LogManager.getLogger();

	private final RuntimeProperties properties;

	public RadixNodeModule(RuntimeProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void configure() {
		bind(RuntimeProperties.class).toInstance(properties);

		// Consensus configuration
		// These cannot be changed without introducing possibilities of
		// going out of sync with consensus.
		bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(properties.get("bft.sync.patience", 200));
		// Default values mean that pacemakers will sync if they are within 5 views of each other.
		// 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
		bindConstant().annotatedWith(PacemakerTimeout.class).to(3000L);
		bindConstant().annotatedWith(PacemakerRate.class).to(1.1);
		bindConstant().annotatedWith(PacemakerMaxExponent.class).to(0);

		// Mempool configuration
		var mempoolMaxSize = properties.get("mempool.maxSize", 10000);
		install(MempoolConfig.asModule(mempoolMaxSize, 5, 60000, 60000, 100));

		// Sync configuration
		final long syncPatience = properties.get("sync.patience", 5000L);
		bind(SyncConfig.class).toInstance(SyncConfig.of(syncPatience, 10, 3000L));

		// Radix Engine configuration
		// These cannot be changed without introducing possible forks with
		// the network.
		// TODO: Move these deeper into radix engine.
		install(RadixEngineConfig.asModule(1, 100, 50));

		// System (e.g. time, random)
		install(new SystemModule());

		install(new RxEnvironmentModule());

		install(new DispatcherModule());

		// Application
		install(new NodeApplicationModule());

		// Consensus
		install(new PersistedBFTKeyModule());
		install(new CryptoModule());
		install(new ConsensusModule());

		// Ledger
		install(new LedgerModule());
		install(new MempoolReceiverModule());

		// Mempool Relay
		install(new MempoolRelayerModule());

		// Sync
		install(new SyncServiceModule());

		// Epochs - Consensus
		install(new EpochsConsensusModule());
		// Epochs - Ledger
		install(new EpochsLedgerUpdateModule());
		// Epochs - Sync
		install(new EpochsSyncModule());

		// State Computer
		install(new ForksModule());
		install(new BetanetForkConfigsModule());
		if (properties.get("overwrite_forks.enable", false)) {
			log.info("Enabling fork overwrites");
			install(new ForkOverwritesFromPropertiesModule());
		}
		install(new RadixEngineStateComputerModule());
		install(new RadixEngineModule());
		install(new RadixEngineStoreModule());

		// Checkpoints
		install(new RadixEngineCheckpointModule());

		var universeModule = new UniverseModule();
		install(universeModule);

		// Storage
		install(new DatabasePropertiesModule());
		install(new PersistenceModule());
		install(new ConsensusRecoveryModule());
		install(new LedgerRecoveryModule());

		// System Info
		install(new SystemInfoModule());

		// Network
		install(new MessagingModule());
		install(new MessageCentralModule(properties));
		install(new HostIpModule(properties));
		install(new P2PModule(properties));
		install(new PeerDiscoveryModule());
		install(new PeerLivenessMonitorModule());

		// API
		configureApi(universeType(universeModule));
	}

	private UniverseType universeType(UniverseModule universeModule) {
		try {
			return universeModule.universe(properties, DefaultSerialization.getInstance()).type();
		} catch (NullPointerException e) {
			return UniverseType.PRODUCTION;    //Assume production environment with relevant restrictions
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load universe", e);
		}
	}

	private void configureApi(UniverseType universeType) {
		var archiveEndpoints = enabledArchiveEndpoints(properties, universeType);
		var nodeEndpoints = enabledNodeEndpoints(properties, universeType);
		var statuses = endpointStatuses(properties, universeType);

		bind(new TypeLiteral<List<EndpointStatus>>() {}).annotatedWith(Endpoints.class).toInstance(statuses);

		if (hasActiveEndpoints(archiveEndpoints, nodeEndpoints)) {
			install(new CommonApiModule());
		}

		if (!archiveEndpoints.isEmpty()) {
			install(new ArchiveApiModule(archiveEndpoints));
		}

		if (!nodeEndpoints.isEmpty()) {
			if (archiveEndpoints.isEmpty()) {
				bind(RriParser.class).toInstance(Rri::rriParser);
			}

			install(new NodeApiModule(nodeEndpoints));
		}
	}

	private boolean hasActiveEndpoints(List<EndpointConfig> archiveEndpoints, List<EndpointConfig> nodeEndpoints) {
		return !archiveEndpoints.isEmpty() || !nodeEndpoints.isEmpty();
	}

	@Provides
	@Singleton
	LocalSystem localSystem(@Self BFTNode self, InfoSupplier infoSupplier) {
		return LocalSystem.create(self, infoSupplier);
	}
}
