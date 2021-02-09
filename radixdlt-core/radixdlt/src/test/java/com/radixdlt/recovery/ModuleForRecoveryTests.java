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

package com.radixdlt.recovery;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.radixdlt.ConsensusModule;
import com.radixdlt.CryptoModule;
import com.radixdlt.DispatcherModule;
import com.radixdlt.EpochsConsensusModule;
import com.radixdlt.EpochsLedgerUpdateModule;
import com.radixdlt.EpochsSyncModule;
import com.radixdlt.LedgerCommandGeneratorModule;
import com.radixdlt.LedgerModule;
import com.radixdlt.LedgerRecoveryModule;
import com.radixdlt.NoFeeModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.statecomputer.RadixEngineModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.statecomputer.RadixEngineValidatorComputersModule;
import com.radixdlt.ConsensusRecoveryModule;
import com.radixdlt.SyncServiceModule;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.environment.MockedCheckpointModule;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.mempool.EmptyMempool;
import com.radixdlt.mempool.Mempool;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.sync.SyncPatienceMillis;

/**
 * Helper class for modules to be used for recovery tests.
 */
public final class ModuleForRecoveryTests {
	private ModuleForRecoveryTests() {
		throw new UnsupportedOperationException("Cannot instantiate.");
	}

	public static Module create() {
		final RadixAddress nativeTokenAddress = new RadixAddress((byte) 0, ECKeyPair.generateNew().getPublicKey());
		final RRI nativeToken = RRI.of(nativeTokenAddress, "NOSUCHTOKEN");
		return Modules.combine(
			new AbstractModule() {
				@Override
				public void configure() {
					bindConstant().annotatedWith(Names.named("magic")).to(0);
					bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
					bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
					bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(Integer.MAX_VALUE);
					bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
					bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
					bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
					bind(RRI.class).annotatedWith(NativeToken.class).toInstance(nativeToken);

					bind(Mempool.class).to(EmptyMempool.class);

					// System
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
				}
			},
			new MockedCheckpointModule(),

			new DeterministicEnvironmentModule(),
			new DispatcherModule(),

			// Consensus
			new CryptoModule(),
			new ConsensusModule(),

			// Ledger
			new LedgerModule(),
			new LedgerCommandGeneratorModule(),

			// Sync
			new SyncServiceModule(),

			// Epochs - Consensus
			new EpochsConsensusModule(),
			// Epochs - Ledger
			new EpochsLedgerUpdateModule(),
			// Epochs - Sync
			new EpochsSyncModule(),

			// State Computer
			new RadixEngineModule(),
			new RadixEngineStoreModule(),
			new RadixEngineValidatorComputersModule(),

			// Fees
			new NoFeeModule(),

			new PersistenceModule(),

			new ConsensusRecoveryModule(),
			new LedgerRecoveryModule()
		);
	}
}
