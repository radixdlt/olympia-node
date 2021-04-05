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

import com.radixdlt.api.ApiModule;
import com.radixdlt.statecomputer.transaction.EmptyTransactionCheckModule;
import com.radixdlt.statecomputer.transaction.TokenFeeModule;
import com.radixdlt.mempool.MempoolConfig;
import org.radix.universe.system.LocalSystem;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.application.NodeApplicationModule;
import com.radixdlt.application.validator.ValidatorRegistratorModule;
import com.radixdlt.chaos.ChaosModule;
import com.radixdlt.client.ClientApiModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
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
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.statecomputer.RadixEngineValidatorComputersModule;
import com.radixdlt.statecomputer.checkpoint.RadixEngineCheckpointModule;
import com.radixdlt.store.DatabasePropertiesModule;
import com.radixdlt.store.PersistenceModule;
import com.radixdlt.sync.SyncConfig;
import com.radixdlt.sync.SyncRunnerModule;
import com.radixdlt.universe.UniverseModule;

/**
 * Module which manages everything in a single node
 */
public final class RadixNodeModule extends AbstractModule {
	private final RuntimeProperties properties;

	public RadixNodeModule(RuntimeProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void configure() {
		bind(RuntimeProperties.class).toInstance(properties);
		bind(MempoolConfig.class).toInstance(MempoolConfig.of(properties.get("mempool.maxSize", 1000), 5L));
		final long syncPatience = properties.get("sync.patience", 2000L);
		bind(SyncConfig.class).toInstance(SyncConfig.of(syncPatience, 10, 3000L));
		bindConstant().annotatedWith(BFTSyncPatienceMillis.class).to(properties.get("bft.sync.patience", 200));
		bindConstant().annotatedWith(MinValidators.class).to(properties.get("consensus.min_validators", 1));
		bindConstant().annotatedWith(MaxValidators.class).to(properties.get("consensus.max_validators", 100));
		bind(View.class).annotatedWith(EpochCeilingView.class)
			.toInstance(View.of(properties.get("epochs.views_per_epoch", 10000L)));

		// Default values mean that pacemakers will sync if they are within 5 views of each other.
		// 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
		bindConstant().annotatedWith(PacemakerTimeout.class).to(properties.get("consensus.pacemaker_timeout_millis", 1000L));
		bindConstant().annotatedWith(PacemakerRate.class).to(properties.get("consensus.pacemaker_rate", 2.0));
		bindConstant().annotatedWith(PacemakerMaxExponent.class).to(properties.get("consensus.pacemaker_max_exponent", 6));

		// System (e.g. time, random)
		install(new SystemModule());

		install(new RxEnvironmentModule());

		install(new DispatcherModule());

		// API
		install(new ApiModule());
		if (properties.get("client_api.enable", false)) {
			install(new ClientApiModule());
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
		install(new SyncRunnerModule());
		install(new SyncServiceModule());

		// Epochs - Consensus
		install(new EpochsConsensusModule());
		// Epochs - Ledger
		install(new EpochsLedgerUpdateModule());
		// Epochs - Sync
		install(new EpochsSyncModule());

		// State Computer
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

		// Application
		install(new NodeApplicationModule());
		install(new ValidatorRegistratorModule());

		if (properties.get("chaos.enable", false)) {
			install(new ChaosModule());
		}
	}

	@Provides
	@Singleton
	LocalSystem localSystem(
		@Self BFTNode self,
		InfoSupplier infoSupplier,
		HostIp hostIp,
		TCPConfiguration tcpConfiguration
	) {
		String host = hostIp.hostIp().orElseThrow(() -> new IllegalStateException("Unable to determine host IP"));
		return LocalSystem.create(self, infoSupplier, host, tcpConfiguration.networkPort(30000));
	}
}
