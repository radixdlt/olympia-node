/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt;

import com.google.common.util.concurrent.RateLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.radixdlt.keys.InMemoryBFTKeyModule;
import com.radixdlt.middleware2.network.GetVerticesRequestRateLimit;
import com.radixdlt.statecomputer.RadixEngineValidatorComputersModule;
import com.radixdlt.consensus.bft.PacemakerMaxExponent;
import com.radixdlt.consensus.bft.PacemakerRate;
import com.radixdlt.consensus.bft.PacemakerTimeout;
import com.radixdlt.consensus.sync.BFTSyncPatienceMillis;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.checkpoint.MockedCheckpointModule;
import com.radixdlt.environment.deterministic.DeterministicEnvironmentModule;
import com.radixdlt.network.TimeSupplier;
import com.radixdlt.statecomputer.MaxValidators;
import com.radixdlt.statecomputer.MinValidators;
import com.radixdlt.store.DatabaseCacheSize;
import com.radixdlt.sync.SyncPatienceMillis;

/**
 * Helper class for modules to be used for recovery tests.
 */
public final class PersistedNodeForTestingModule extends AbstractModule {
	@Override
	public void configure() {
		bind(Integer.class).annotatedWith(SyncPatienceMillis.class).toInstance(200);
		bind(Integer.class).annotatedWith(BFTSyncPatienceMillis.class).toInstance(200);
		bind(Integer.class).annotatedWith(MinValidators.class).toInstance(1);
		bind(Integer.class).annotatedWith(MaxValidators.class).toInstance(Integer.MAX_VALUE);
		bind(Long.class).annotatedWith(PacemakerTimeout.class).toInstance(1000L);
		bind(Double.class).annotatedWith(PacemakerRate.class).toInstance(2.0);
		bind(Integer.class).annotatedWith(PacemakerMaxExponent.class).toInstance(6);
		bind(RateLimiter.class).annotatedWith(GetVerticesRequestRateLimit.class)
			.toInstance(RateLimiter.create(Double.MAX_VALUE));
		bindConstant().annotatedWith(DatabaseCacheSize.class)
			.to((long) (Runtime.getRuntime().maxMemory() * 0.125));

		// System
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(TimeSupplier.class).toInstance(System::currentTimeMillis);

		install(new InMemoryBFTKeyModule());
		install(new MockedCheckpointModule());
		install(new CryptoModule());
		install(new DeterministicEnvironmentModule());
		install(new FunctionalNodeModule());
		install(new RadixEngineStoreModule());
		install(new RadixEngineValidatorComputersModule());
		install(new PersistenceModule());
		install(new ConsensusRecoveryModule());
		install(new LedgerRecoveryModule());
	}
}
