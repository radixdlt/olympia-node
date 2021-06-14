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
import com.radixdlt.properties.RuntimeProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ForkOverwritesFromPropertiesModule extends AbstractModule {
	private static Logger logger = LogManager.getLogger();

	@Override
	protected void configure() {
		install(new RadixEngineForksOverwriteForTestingModule());
	}

	@Provides
	@Singleton
	private Map<String, ForkConfig> configOverwrite(ImmutableList<ForkConfig> forkConfigs, RuntimeProperties properties) {
		var overwrites = new HashMap<String, ForkConfig>();
		forkConfigs.forEach(e -> {
			final var viewOverwrite = properties.get("overwrite_forks." + e.getName() + ".views", "");
			final var epochOverwrite = properties.get("overwrite_forks." + e.getName() + ".epoch", "");
			if (!viewOverwrite.isBlank() || !epochOverwrite.isBlank()) {
				final var view = viewOverwrite.isBlank()
					? e.getEpochCeilingView()
					: View.of(Long.parseLong(viewOverwrite));

				final var epoch = epochOverwrite.isBlank()
					? e.getExecutedAtEpoch()
					: Optional.of(Long.parseLong(epochOverwrite));

				logger.warn("Overwriting epoch of " + e.getName() + " from " + e.getExecutedAtEpoch() + " to " + epoch);
				logger.warn("Overwriting views of " + e.getName() + " from " + e.getEpochCeilingView() + " to " + view);

				var overwrite = new ForkConfig(
					e.getName(),
					epoch,
					epoch.isPresent() ? 0 : e.getRequiredVotingStakePercentage(),
					e.getParser(),
					e.getSubstateSerialization(),
					e.getConstraintMachineConfig(),
					e.getActionConstructors(),
					e.getBatchVerifier(),
					e.getPostProcessedVerifier(),
					view
				);
				overwrites.put(e.getName(), overwrite);
			}
		});
		return overwrites;
	}
}
