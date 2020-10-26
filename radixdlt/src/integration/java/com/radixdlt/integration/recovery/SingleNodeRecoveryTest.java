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

public class SingleNodeRecoveryTest {
	/*
	private void setup() {
		Guice.createInjector(
			new AbstractModule() {
				@Override
				protected void configure() {
					bind(DatabaseEnvironment.class).toInstance(dbEnv);
					bind(Universe.class).toInstance(universe);
					bind(LocalSystem.class).toProvider(LocalSystemProvider.class).in(Scopes.SINGLETON);

					bind(EUID.class).annotatedWith(Names.named("self")).toProvider(SelfNidProvider.class);
					bind(ECKeyPair.class).annotatedWith(Names.named("self")).toProvider(SelfKeyPairProvider.class);
					bind(ECPublicKey.class).annotatedWith(Names.named("self")).toProvider(SelfPublicKeyProvider.class);
					bind(RadixAddress.class).annotatedWith(Names.named("self")).toProvider(SelfAddressProvider.class);
					bind(BFTNode.class).annotatedWith(Names.named("self")).toProvider(SelfBFTNodeProvider.class);

					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
				}

				// Default values mean that pacemakers will sync if they are within 5 views of each other.
				// 5 consecutive failing views will take 1*(2^6)-1 seconds = 63 seconds.
				@Provides
				@PacemakerTimeout
				long pacemakerTimeout() {
					return 1000L;
				}

				@Provides
				@PacemakerRate
				double pacemakerRate() {
					return 2.0;
				}

				@Provides
				@PacemakerMaxExponent
				int pacemakerMaxExponent() {
					return 6;
				}

				@Provides
				@EpochCeilingView
				View epochCeilingView() {
					return View.of(100L);
				}
			},

			// Consensus
			new CryptoModule(),
			new ConsensusModule(),
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
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			new EpochsLedgerUpdateRxModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineRxModule(),
			new RadixEngineStoreModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule()
		);
	}


	 */
}
