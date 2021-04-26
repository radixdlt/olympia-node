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

import com.radixdlt.api.NodeApiModule;

import com.radixdlt.api.faucet.FaucetModule;
import com.radixdlt.statecomputer.RadixEngineConfig;
import com.radixdlt.statecomputer.RadixEngineStateComputerModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.radixdlt.statecomputer.transaction.EmptyTransactionCheckModule;
import com.radixdlt.statecomputer.transaction.TokenFeeModule;
import com.radixdlt.mempool.MempoolConfig;
import org.radix.universe.system.LocalSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.chaos.ChaosModule;
import com.radixdlt.client.ArchiveApiModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.environment.rx.RxEnvironmentModule;
import com.radixdlt.keys.PersistedBFTKeyModule;
import com.radixdlt.mempool.MempoolReceiverModule;
import com.radixdlt.mempool.MempoolRelayerModule;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.network.NetworkModule;
import com.radixdlt.network.addressbook.AddressBookModule;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.transport.tcp.TCPConfiguration;
import com.radixdlt.network.transport.tcp.TCPTransportModule;
import com.radixdlt.network.transport.udp.UDPTransportModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineValidatorComputersModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.universe.UniverseModule;

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
		bindConstant().annotatedWith(PacemakerRate.class).to(2.0);
		bindConstant().annotatedWith(PacemakerMaxExponent.class).to(6);

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
		install(RadixEngineConfig.asModule(1, 100, 100000, 50));

		// System (e.g. time, random)
		install(new SystemModule());

		install(new RxEnvironmentModule());

		install(new DispatcherModule());


		// Application
		install(new NodeApplicationModule());

		// API
		install(new NodeApiModule());
		if (properties.get("client_api.enable", false)) {
			log.info("Enabling high level API");
			install(new ArchiveApiModule());
		}
		if (properties.get("faucet.enable", false)) {
			log.info("Enabling faucet API");
			install(new FaucetModule());
		}
		if (properties.get("chaos.enable", false)) {
			log.info("Enabling chaos API");
			install(new ChaosModule());
		}

		// Consensus
		install(new PersistedBFTKeyModule());
		install(new CryptoModule());
		install(new ConsensusModule());
		install(new ConsensusRunnerModule());

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
		install(new RadixEngineStateComputerModule());
		install(new RadixEngineModule());
		install(new RadixEngineValidatorComputersModule());
		install(new RadixEngineStoreModule());

		// Post constraint checkers - Fees, emptiness
		install(new EmptyTransactionCheckModule());
		install(new TokenFeeModule());

		// Checkpoints
		install(new RadixEngineCheckpointModule());
		install(new UniverseModule());

		// Storage
		install(new DatabasePropertiesModule());
		install(new PersistenceModule());
		install(new ConsensusRecoveryModule());
		install(new LedgerRecoveryModule());

		// System Info
		install(new SystemInfoModule());

		// Network
		install(new NetworkModule());
		install(new MessageCentralModule(properties));
		install(new UDPTransportModule(properties));
		install(new TCPTransportModule(properties));
		install(new AddressBookModule());
		install(new HostIpModule(properties));
	}

	@Provides
	@Singleton
	LocalSystem localSystem(
		@Self BFTNode self,
		InfoSupplier infoSupplier,
		HostIp hostIp,
		TCPConfiguration tcpConfiguration
	) {
		final var host = hostIp.hostIp().orElseThrow(() -> new IllegalStateException("Unable to determine host IP"));
		final var listenPort = tcpConfiguration.listenPort(30000);
		final var broadcastPort = tcpConfiguration.broadcastPort(listenPort); // defaults to listen port
		return LocalSystem.create(self, infoSupplier, host, broadcastPort);
	}
}
