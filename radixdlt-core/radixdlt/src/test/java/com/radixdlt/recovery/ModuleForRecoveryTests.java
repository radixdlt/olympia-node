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

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;
import com.radixdlt.CryptoModule;
import com.radixdlt.FunctionalNodeModule;
import com.radixdlt.LedgerRecoveryModule;
import com.radixdlt.PersistenceModule;
import com.radixdlt.RadixEngineStoreModule;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.statecomputer.RadixEngineValidatorComputersModule;
import com.radixdlt.ConsensusRecoveryModule;
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
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.store.DatabaseCacheSize;
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
					bind(RateLimiter.class).annotatedWith(GetVerticesRequestRateLimit.class)
						.toInstance(RateLimiter.create(Double.MAX_VALUE));
					bind(RRI.class).annotatedWith(NativeToken.class).toInstance(nativeToken);
					bindConstant().annotatedWith(DatabaseCacheSize.class)
						.to((long) (Runtime.getRuntime().maxMemory() * 0.125));

					// System
					bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
					bind(TimeSupplier.class).toInstance(System::currentTimeMillis);
				}
			},
			new MockedCheckpointModule(),
			new CryptoModule(),
			new DeterministicEnvironmentModule(),
			new FunctionalNodeModule(),
			new RadixEngineStoreModule(),
			new RadixEngineValidatorComputersModule(),
			new PersistenceModule(),
			new ConsensusRecoveryModule(),
			new LedgerRecoveryModule()
		);
	}
}
