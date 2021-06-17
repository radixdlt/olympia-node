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

import java.util.Map;
import java.util.Optional;
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
	private Map<String, ForkConfig> configOverwrite(ImmutableList<ForkConfig> forkConfigs) {
		final var epoch = new AtomicLong(0);
		final var viewCeiling = new AtomicLong(INITIAL_VIEW_CEILING);
		return forkConfigs.stream()
			.collect(
				Collectors.toMap(
					ForkConfig::getName,
					e -> new ForkConfig(
						e.getName(),
						e.getExecutedAtEpoch().isPresent() ? Optional.of(epoch.getAndAdd(5)) : Optional.empty(),
						e.getRequiredVotingStakePercentage(),
						e.getParser(),
						e.getSubstateSerialization(),
						fees ? e.getConstraintMachineConfig()
							: e.getConstraintMachineConfig().metering((procedureKey, param, context) -> { }),
						e.getActionConstructors(),
						e.getBatchVerifier(),
						fees ? e.getPostProcessedVerifier() : (p, t) -> { },
						View.of(viewCeiling.get() % 2 == 0 ? viewCeiling.getAndIncrement() : viewCeiling.getAndDecrement())
					)
			));
	}
}
