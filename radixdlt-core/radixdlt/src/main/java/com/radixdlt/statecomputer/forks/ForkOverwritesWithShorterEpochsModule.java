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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class ForkOverwritesWithShorterEpochsModule extends AbstractModule {
	private static final long INITIAL_VIEW_CEILING = 10L;
	private final boolean fees;

	public ForkOverwritesWithShorterEpochsModule(boolean fees) {
		this.fees = fees;
	}

	@Override
	protected void configure() {
		install(new RadixEngineForksOverwriteForTestingModule());
	}

	@Provides
	@Singleton
	private Map<String, Long> epochOverwrite(Map<EpochMapKey, ForkConfig> forkConfigs) {
		var epoch = new AtomicLong(0);
		return forkConfigs.entrySet().stream()
			.collect(
				Collectors.toMap(
					e -> e.getValue().getName(),
					e -> epoch.getAndAdd(5)
				));
	}

	@Provides
	@Singleton
	private Map<String, ForkConfig> configOverwrite(Map<EpochMapKey, ForkConfig> forkConfigs) {
		var viewCeiling = new AtomicLong(INITIAL_VIEW_CEILING);
		return forkConfigs.entrySet().stream()
			.collect(
				Collectors.toMap(
					e -> e.getValue().getName(),
					e -> new ForkConfig(
						e.getValue().getName(),
						e.getValue().getParser(),
						e.getValue().getSubstateSerialization(),
						fees ? e.getValue().getConstraintMachineConfig()
							: e.getValue().getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
						e.getValue().getActionConstructors(),
						e.getValue().getBatchVerifier(),
						fees ? e.getValue().getPostProcessedVerifier() : (p, t) -> { },
						View.of(viewCeiling.get() % 2 == 0 ? viewCeiling.getAndIncrement() : viewCeiling.getAndDecrement())
					)
			));
	}
}
