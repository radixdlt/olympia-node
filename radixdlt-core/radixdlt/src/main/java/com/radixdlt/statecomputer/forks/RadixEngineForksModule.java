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
 *
 */

package com.radixdlt.statecomputer.forks;

import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.LedgerProof;
import com.radixdlt.sync.CommittedReader;

/**
 * Module responsible for creating a ForkManager for use in the RadixEngine
 */
public final class RadixEngineForksModule extends AbstractModule {

	@Provides
	@Singleton
	private ForkManager forkManager(ImmutableList<ForkConfig> forksConfigs) {
		return new ForkManager(forksConfigs);
	}

	@Provides
	@Singleton
	private ForkConfig forkConfig(
		CommittedReader committedReader, // TODO: This is a hack, remove
		ForkManager forkManager
	) {
		final var lastProof = committedReader.getLastProof().orElse(LedgerProof.mock());
		final var epoch = lastProof.isEndOfEpoch() ? lastProof.getEpoch() + 1 : lastProof.getEpoch();
		final var maybeEpochProof = committedReader.getEpochProof(epoch);
		return forkManager.currentFork(epoch, maybeEpochProof);
	}
}
