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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.radixdlt.consensus.bft.View;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * For testing only, only tests the latest state computer configuration
 */
public class RadixEngineForksLatestOnlyModule extends AbstractModule {
	private final View epochHighViewOverwrite;
	private final boolean fees;

	public RadixEngineForksLatestOnlyModule(View epochHighViewOverwrite, boolean fees) {
		this.epochHighViewOverwrite = epochHighViewOverwrite;
		this.fees = fees;
	}

	@Provides
	@Singleton
	private ForkConfig forkConfig(Map<EpochMapKey, ForkConfig> forkConfigs) {
		return forkConfigs.entrySet().stream()
			.max(Comparator.comparing(e -> e.getKey().epoch()))
			.map(Map.Entry::getValue)
			.map(f -> new ForkConfig(
				f.getName(),
				f.getParser(),
				fees ? f.getConstraintMachineConfig() : f.getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
				f.getActionConstructors(),
				f.getBatchVerifier(),
				fees ? f.getPostProcessedVerifier() : (p, t) -> { },
				epochHighViewOverwrite
			))
			.orElseThrow();
	}

	@Provides
	@Singleton
	private TreeMap<Long, ForkConfig> epochToForkConfig(ForkConfig forkConfig) {
		return new TreeMap<>(Map.of(0L, forkConfig));
	}
}
