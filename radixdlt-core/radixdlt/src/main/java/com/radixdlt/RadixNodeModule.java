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

import com.radixdlt.statecomputer.forks.TestingForksModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import org.radix.utils.IOUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.radixdlt.api.module.ArchiveApiModule;
import com.radixdlt.api.module.CommonApiModule;
import com.radixdlt.api.module.NodeApiModule;
import com.radixdlt.api.qualifier.Endpoints;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.atom.Txn;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.engine.RadixEngineException;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.keys.PersistedBFTKeyModule;
import com.radixdlt.ledger.VerifiedTxnsAndProof;
import com.radixdlt.mempool.MempoolConfig;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.messaging.MessagingModule;
import com.radixdlt.network.p2p.P2PModule;
import com.radixdlt.network.p2p.PeerDiscoveryModule;
import com.radixdlt.network.p2p.PeerLivenessMonitorModule;
import com.radixdlt.networks.Addressing;
import com.radixdlt.networks.Network;
import com.radixdlt.networks.NetworkId;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineStateComputerModule;
import com.radixdlt.statecomputer.checkpoint.Genesis;
import com.radixdlt.statecomputer.checkpoint.GenesisBuilder;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.statecomputer.forks.ForksModule;
import com.radixdlt.statecomputer.forks.ForkOverwritesFromPropertiesModule;
import com.radixdlt.statecomputer.forks.MainnetForksModule;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.utils.Bytes;

import java.io.FileInputStream;
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
	private final int networkId;

	public RadixNodeModule(RuntimeProperties properties) {
		this.properties = properties;
		var networkId = properties.get("network.id");
		if (networkId == null) {
			throw new IllegalStateException("Must specify network.id");
		}
		this.networkId = Integer.parseInt(networkId);
	}

	@Provides
	@Genesis
	@Singleton
	VerifiedTxnsAndProof genesis(@Genesis Txn genesis, GenesisBuilder genesisBuilder) throws RadixEngineException {
		var proof = genesisBuilder.generateGenesisProof(genesis);
		return VerifiedTxnsAndProof.create(List.of(genesis), proof);
	}

	private Txn loadGenesisFile(String genesisFile) {
		try (var genesisJsonString = new FileInputStream(genesisFile)) {
			var genesisJson = new JSONObject(IOUtils.toString(genesisJsonString));
			var genesisHex = genesisJson.getString("genesis");
			return Txn.create(Bytes.fromHexString(genesisHex));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private Txn loadGenesis(int networkId) {
		var genesisTxnHex = properties.get("network.genesis_txn");
		var genesisFile = properties.get("network.genesis_file");
		var network = Network.ofId(networkId);
		var networkGenesis = network
			.flatMap(Network::genesisTxn)
			.map(Bytes::fromHexString)
			.map(Txn::create);

		if (networkGenesis.isPresent()) {
			if (Strings.isNotBlank(genesisTxnHex)) {
				throw new IllegalStateException("Cannot provide genesis txn for well-known network " + network.orElseThrow());
			}

			if (Strings.isNotBlank(genesisFile)) {
				throw new IllegalStateException("Cannot provide genesis file for well-known network " + network.orElseThrow());
			}
			return networkGenesis.get();
		} else {
			var genesisCount = 0;
			genesisCount += Strings.isNotBlank(genesisTxnHex) ? 1 : 0;
			genesisCount += Strings.isNotBlank(genesisFile) ? 1 : 0;
			if (genesisCount > 1) {
				throw new IllegalStateException("Multiple genesis txn specified.");
			}
			if (genesisCount == 0) {
				throw new IllegalStateException("No genesis txn specified.");
			}
			return Strings.isNotBlank(genesisTxnHex) ? Txn.create(Bytes.fromHexString(genesisTxnHex)) : loadGenesisFile(genesisFile);
		}
	}

	@Override
	protected void configure() {
		if (this.networkId <= 0) {
			throw new IllegalStateException("Illegal networkId " + networkId);
		}

		var addressing = Addressing.ofNetworkId(networkId);
		bind(Addressing.class).toInstance(addressing);
		bindConstant().annotatedWith(NetworkId.class).to(networkId);
		bind(Txn.class).annotatedWith(Genesis.class).toInstance(loadGenesis(networkId));
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

		// System (e.g. time, random)
		install(new SystemModule());

		install(new RxEnvironmentModule());

		install(new EventLoggerModule());
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
		// Epochs - Sync
		install(new EpochsSyncModule());

		// State Computer
		install(new ForksModule());

		if (properties.get("testing_forks.enable", false)) {
			log.info("Enabling testing forks");
			install(new TestingForksModule());
		} else {
			install(new MainnetForksModule());
		}

		if (properties.get("overwrite_forks.enable", false)) {
			log.info("Enabling fork overwrites");
			install(new ForkOverwritesFromPropertiesModule());
		}
		install(new RadixEngineStateComputerModule());
		install(new RadixEngineModule());
		install(new RadixEngineStoreModule());

		// Checkpoints
		install(new RadixEngineCheckpointModule());

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
		configureApi();
	}

	private void configureApi() {
		var archiveEndpoints = enabledArchiveEndpoints(properties, networkId);
		var nodeEndpoints = enabledNodeEndpoints(properties, networkId);
		var statuses = endpointStatuses(properties, networkId);

		bind(new TypeLiteral<List<EndpointStatus>>() {}).annotatedWith(Endpoints.class).toInstance(statuses);

		if (hasActiveEndpoints(archiveEndpoints, nodeEndpoints)) {
			install(new CommonApiModule());
		}

		if (!archiveEndpoints.isEmpty()) {
			install(new ArchiveApiModule(archiveEndpoints));
		}

		if (!nodeEndpoints.isEmpty()) {
			install(new NodeApiModule(nodeEndpoints));
		}
	}

	private boolean hasActiveEndpoints(List<EndpointConfig> archiveEndpoints, List<EndpointConfig> nodeEndpoints) {
		return !archiveEndpoints.isEmpty() || !nodeEndpoints.isEmpty();
	}
}
