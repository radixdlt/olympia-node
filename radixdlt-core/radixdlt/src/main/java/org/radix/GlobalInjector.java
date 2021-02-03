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

package org.radix;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.BFTKeyModule;
import com.radixdlt.ChaosModule;
import com.radixdlt.CheckpointModule;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRunnerModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.DispatcherModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.GenesisValidatorSetFromUniverseModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsLedgerUpdateRxModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerRecoveryModule;
import com.radixdlt.MempoolRelayerModule;
import com.radixdlt.MempoolReceiverModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.ConsensusRecoveryModule;
import com.radixdlt.RxEnvironmentModule;
import com.radixdlt.RadixEngineValidatorComputersModule;
import com.radixdlt.SyncRunnerModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.SystemModule;
import com.radixdlt.TokenFeeModule;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.bft.View;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.middleware2.InfoSupplier;
import com.radixdlt.SystemInfoModule;
import com.radixdlt.NetworkModule;
import com.radixdlt.network.addressbook.AddressBookModule;
import com.radixdlt.network.hostip.HostIp;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.transport.tcp.TCPTransportModule;
import com.radixdlt.network.transport.udp.UDPTransportModule;
import com.radixdlt.properties.RuntimeProperties;
import com.radixdlt.statecomputer.EpochCeilingView;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.sync.SyncPatienceMillis;
import com.radixdlt.universe.Universe;

import org.radix.universe.system.LocalSystem;

public class GlobalInjector {

	private Injector injector;

	public GlobalInjector(RuntimeProperties properties) {
		final int mempoolMaxSize = properties.get("mempool.maxSize", 1000);

		// temporary global module to hook up global things
		Module globalModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(properties);
				bindConstant().annotatedWith(SyncPatienceMillis.class).to(properties.get("sync.patience", 200));
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

				// Consensus
				install(new BFTKeyModule());
				install(new CryptoModule());
				install(new ConsensusModule());
				install(new ConsensusRxModule());
				install(new ConsensusRunnerModule());

				// Ledger
				install(new LedgerModule());
				install(new LedgerCommandGeneratorModule());
				install(new LedgerLocalMempoolModule(mempoolMaxSize));

				// Mempool Relay
				install(new MempoolReceiverModule());
				install(new MempoolRelayerModule());

				// Sync
				install(new SyncRunnerModule());
				install(new SyncServiceModule());

				// Epochs - Consensus
				install(new EpochsConsensusModule());
				// Epochs - Ledger
				install(new EpochsLedgerUpdateModule());
				install(new EpochsLedgerUpdateRxModule());
				// Epochs - Sync
				install(new EpochsSyncModule());

				// State Computer
				install(new RadixEngineModule());
				install(new RadixEngineValidatorComputersModule());
				install(new RadixEngineStoreModule());

				// Checkpoints
				install(new CheckpointModule());

				// Genesis validators
				install(new GenesisValidatorSetFromUniverseModule());

				// Fees
				install(new TokenFeeModule());

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

				if (properties.get("chaos.enable", false)) {
					install(new ChaosModule());
				}
			}

			@Provides
			@Singleton
			LocalSystem localSystem(
				@Self BFTNode self,
				InfoSupplier infoSupplier,
				Universe universe,
				HostIp hostIp
			) {
				String host = hostIp.hostIp().orElseThrow(() -> new IllegalStateException("Unable to determine host IP"));
				return LocalSystem.create(self, infoSupplier, universe, host);
			}
		};

		injector = Guice.createInjector(
			globalModule
		);
	}

	public Injector getInjector() {
		return injector;
	}
}
