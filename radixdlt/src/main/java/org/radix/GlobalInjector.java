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
import com.radixdlt.CheckpointModule;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRunnerModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsLedgerUpdateRxModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineRxModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.RadixEngineValidatorComputersModule;
import com.radixdlt.SyncRunnerModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.SyncMempoolServiceModule;
import com.radixdlt.LedgerRxModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.SyncRxModule;
import com.radixdlt.SystemInfoRxModule;
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
		// temporary global module to hook up global things
		Module globalModule = new AbstractModule() {
			@Override
			protected void configure() {
				bind(RuntimeProperties.class).toInstance(properties);
				bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
				bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
				bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
				bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(100);
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

			// Default values mean that pacemakers will sync if they are within 5 views of each other.
			// 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
			@Provides
			@PacemakerTimeout
			long pacemakerTimeout() {
				return properties.get("consensus.pacemaker_timeout_millis", 1000L);
			}

			@Provides
			@PacemakerRate
			double pacemakerRate() {
				return properties.get("consensus.pacemaker_rate", 2.0);
			}

			@Provides
			@PacemakerMaxExponent
			int pacemakerMaxExponent() {
				return properties.get("consensus.pacemaker_max_exponent", 6);
			}

			@Provides
			@EpochCeilingView
			View epochCeilingView() {
				return View.of(properties.get("epochs.views_per_epoch", 100L));
			}
		};

		final int fixedNodeCount = properties.get("consensus.fixed_node_count", 1);
		final int mempoolMaxSize = properties.get("mempool.maxSize", 1000);

		injector = Guice.createInjector(
			// System (e.g. time, random)
			new SystemModule(),

			// Consensus
			new BFTKeyModule(),
			new CryptoModule(),
			new ConsensusModule(),
			new ConsensusRxModule(),
			new ConsensusRunnerModule(),

			// Ledger
			new LedgerModule(),
			new LedgerRxModule(),
			new LedgerCommandGeneratorModule(),
			new LedgerLocalMempoolModule(mempoolMaxSize),

			// Sync
			new SyncRunnerModule(),
			new SyncRxModule(),
			new SyncServiceModule(),
			new SyncMempoolServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			new EpochsLedgerUpdateRxModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineValidatorComputersModule(),
			new RadixEngineRxModule(),
			new RadixEngineStoreModule(),

			// Checkpoints
			new CheckpointModule(fixedNodeCount),

			// Fees
			new TokenFeeModule(),

			new PersistenceModule(),

			// System Info
			new SystemInfoModule(properties),
			new SystemInfoRxModule(),

			// Network
			new NetworkModule(),
			new MessageCentralModule(properties),
			new UDPTransportModule(properties),
			new TCPTransportModule(properties),
			new AddressBookModule(),
			new HostIpModule(properties),

			globalModule
		);
	}

	public Injector getInjector() {
		return injector;
	}
}
