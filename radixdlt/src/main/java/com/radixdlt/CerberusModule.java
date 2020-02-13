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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.radixdlt.consensus.Consensus;
import com.radixdlt.consensus.tempo.DumbMemPool;
import com.radixdlt.consensus.tempo.MemPool;
import com.radixdlt.consensus.tempo.Scheduler;
import com.radixdlt.consensus.tempo.SingleThreadedScheduler;
import com.radixdlt.consensus.tempo.ChainedBFT;
import com.radixdlt.consensus.tempo.WallclockTimeSupplier;
import com.radixdlt.engine.RadixEngine;
import com.radixdlt.middleware2.converters.AtomToBinaryConverter;
import com.radixdlt.middleware2.processing.RadixEngineAtomProcessor;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.store.LedgerEntryStore;

import org.radix.time.Time;

// FIXME: static dependency on Time
public class CerberusModule extends AbstractModule {
	@Override
	protected void configure() {
		// dependencies
		bind(Scheduler.class).toProvider(SingleThreadedScheduler::new);
		bind(WallclockTimeSupplier.class).toInstance(Time::currentTimestamp);
		bind(Consensus.class).to(ChainedBFT.class).in(Scopes.SINGLETON);
		bind(MemPool.class).to(DumbMemPool.class);
	}

	// We want to use the same instance for Application and RadixEngineAtomProcessor
	@Provides
	@Singleton
	private DumbMemPool dumbMemPool(AtomToBinaryConverter atomToBinaryConverter) {
		return new DumbMemPool(atomToBinaryConverter);
	}

	// We want to use the same instance for Application and RadixEngineAtomProcessor
	@Provides
	@Singleton
	private RadixEngineAtomProcessor radixEngineAtomProcessorProvider(
		Consensus consensus,
		LedgerEntryStore store,
		RadixEngine radixEngine,
		AtomToBinaryConverter atomToBinaryConverter
	) {
		return new RadixEngineAtomProcessor(consensus, store, radixEngine, atomToBinaryConverter);
	}

}
