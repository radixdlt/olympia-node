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
import com.radixdlt.properties.RuntimeProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class ForkOverwritesFromPropertiesModule extends AbstractModule {
	private static Logger logger = LogManager.getLogger();

	@Override
	protected void configure() {
		install(new RadixEngineForksOverwriteForTestingModule());
	}

	@Provides
	@Singleton
	private Map<String, Long> epochOverwrite(Map<EpochMapKey, ForkConfig> forkConfigs, RuntimeProperties properties) {
		var overwrites = new HashMap<String, Long>();
		forkConfigs.forEach((k, c) -> {
			var viewOverwrite = properties.get("overwrite_forks." + c.getName() + ".epoch", "");
			if (!viewOverwrite.isBlank()) {
				var epoch = Long.parseLong(viewOverwrite);
				logger.warn("Overwriting epoch of " + c.getName() + " from " + k.epoch() + " to " + epoch);
				overwrites.put(c.getName(), epoch);
			}
		});
		return overwrites;
	}

	@Provides
	@Singleton
	private Map<String, ForkConfig> configOverwrite(Map<EpochMapKey, ForkConfig> forkConfigs, RuntimeProperties properties) {
		var overwrites = new HashMap<String, ForkConfig>();
		forkConfigs.forEach((k, c) -> {
			var viewOverwrite = properties.get("overwrite_forks." + c.getName() + ".views", "");
			if (!viewOverwrite.isBlank()) {
				var view = Long.parseLong(viewOverwrite);
				logger.warn("Overwriting views of " + c.getName() + " from " + c.getEpochCeilingView() + " to " + view);
				var overwrite = new ForkConfig(
					c.getName(),
					c.getParser(),
					c.getConstraintMachineConfig(),
					c.getActionConstructors(),
					c.getBatchVerifier(),
					c.getPostProcessedVerifier(),
					View.of(view)
				);
				overwrites.put(c.getName(), overwrite);
			}
		});
		return overwrites;
	}
}
