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
import com.radixdlt.consensus.bft.View;

/**
 * For testing only, only tests the latest state computer configuration
 */
public final class RadixEngineForksLatestOnlyModule extends AbstractModule {
	private final View epochHighViewOverwrite;
	private final boolean fees;

	public RadixEngineForksLatestOnlyModule(View epochHighViewOverwrite, boolean fees) {
		this.epochHighViewOverwrite = epochHighViewOverwrite;
		this.fees = fees;
	}

	@Provides
	@Singleton
	private ForkManager forkManager(ImmutableList<ForkConfig> forksConfigs) {
		return new ForkManager(ImmutableList.of(forksConfigs.get(forksConfigs.size() - 1)));
	}

	@Provides
	@Singleton
	private ForkConfig initialForkConfig(ForkManager forkManager) {
		final var originalForkConfig = forkManager.genesisFork();

		return new ForkConfig(
			originalForkConfig.getName(),
			originalForkConfig.getExecutePredicate(),
			originalForkConfig.getParser(),
			originalForkConfig.getSubstateSerialization(),
			fees ? originalForkConfig.getConstraintMachineConfig()
				: originalForkConfig.getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
			originalForkConfig.getActionConstructors(),
			originalForkConfig.getBatchVerifier(),
			fees ? originalForkConfig.getPostProcessedVerifier() : (p, t) -> { },
			epochHighViewOverwrite
		);
	}
}
