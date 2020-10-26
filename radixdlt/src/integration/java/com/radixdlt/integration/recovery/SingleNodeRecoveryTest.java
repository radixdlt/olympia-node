/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.integration.recovery;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.radixdlt.ConsensusModule;
import com.radixdlt.ConsensusRunnerModule;
import com.radixdlt.ConsensusRxModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsLedgerUpdateRxModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerLocalMempoolModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.LedgerRxModule;
import com.radixdlt.NetworkModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineModule;
import com.radixdlt.RadixEngineRxModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.SyncMempoolServiceModule;
import com.radixdlt.SyncRunnerModule;
import com.radixdlt.SyncRxModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.SystemInfoModule;
import com.radixdlt.SystemInfoRxModule;
import com.radixdlt.SystemModule;
import com.radixdlt.consensus.bft.BFTValidatorSet;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.integration.distributed.MockedLedgerModule;
import com.radixdlt.integration.distributed.MockedMempoolModule;
import com.radixdlt.integration.distributed.MockedStateComputerModule;
import com.radixdlt.integration.distributed.deterministic.DeterministicMempoolModule;
import com.radixdlt.integration.distributed.deterministic.DeterministicTest.Builder.LedgerType;
import com.radixdlt.network.addressbook.AddressBookModule;
import com.radixdlt.network.hostip.HostIpModule;
import com.radixdlt.network.messaging.MessageCentralModule;
import com.radixdlt.network.transport.tcp.TCPTransportModule;
import com.radixdlt.network.transport.udp.UDPTransportModule;

public class SingleNodeRecoveryTest {
	private void setup() {
		Guice.createInjector(
			// System (e.g. time, random)
			new SystemModule(),

			// Consensus
			new CryptoModule(),
			new ConsensusModule(pacemakerTimeout, pacemakerRate, pacemakerMaxExponent),
			new ConsensusRxModule(),
			new ConsensusRunnerModule(),

			// Ledger
			new LedgerModule(),
			new LedgerRxModule(),
			new LedgerCommandGeneratorModule(),
			new MockedMempoolModule(),

			// Sync
			new SyncRunnerModule(),
			new SyncRxModule(),
			new SyncServiceModule(),
			new SyncMempoolServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(pacemakerTimeout, pacemakerRate, pacemakerMaxExponent),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			new EpochsLedgerUpdateRxModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(epochHighView),
			new RadixEngineRxModule(),
			new RadixEngineStoreModule(fixedNodeCount),

			// Fees
			new NoFeeModule(),

			new PersistenceModule(),

			// System Info
			new SystemInfoModule(properties),
		)
	}

}
