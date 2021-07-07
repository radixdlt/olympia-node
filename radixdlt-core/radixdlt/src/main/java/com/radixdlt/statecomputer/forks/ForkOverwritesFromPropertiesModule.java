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
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;
import com.radixdlt.properties.RuntimeProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class ForkOverwritesFromPropertiesModule extends AbstractModule {
	private static final Logger logger = LogManager.getLogger();

	private static class ForkOverwrite implements UnaryOperator<Set<ForkBuilder>> {
		@Inject
		private RuntimeProperties properties;

		@Override
		public Set<ForkBuilder> apply(Set<ForkBuilder> forkBuilders) {
			return forkBuilders.stream()
				.map(c -> {
					final var forkDisabledOverwrite = properties.get("overwrite_forks." + c.getName() + ".disabled", "");
					if (!forkDisabledOverwrite.isBlank()) {
						return Optional.<ForkBuilder>empty();
					}

					final var requiredStakeVotesOverwrite = properties.get("overwrite_forks." + c.getName() + ".required_stake_votes", "");
					final var epochOverwrite = properties.get("overwrite_forks." + c.getName() + ".epoch", "");

					if (!requiredStakeVotesOverwrite.isBlank()) {
						final var requiredStakeVotes = Integer.parseInt(requiredStakeVotesOverwrite);
						final var minEpochOverwrite = properties.get("overwrite_forks." + c.getName() + ".min_epoch", "");
						final var minEpoch = minEpochOverwrite.isBlank()
							? c.fixedOrMinEpoch()
							: Long.parseLong(minEpochOverwrite);
						c = c.withStakeVoting(minEpoch, requiredStakeVotes);
					} else if (!epochOverwrite.isBlank()) {
						final var epoch = Long.parseLong(epochOverwrite);
						logger.warn("Overwriting epoch of " + c.getName() + " to " + epoch);
						c = c.atFixedEpoch(epoch);
					}

					final var viewOverwrite = properties.get("overwrite_forks." + c.getName() + ".views", "");
					if (!viewOverwrite.isBlank()) {
						final var view = Long.parseLong(viewOverwrite);
						logger.warn("Overwriting views of " + c.getName() + " from " + c.getEngineRulesConfig().getMaxRounds() + " to " + view);
						c = c.withEngineRulesConfig(c.getEngineRulesConfig().overrideMaxRounds(view));
					}

					return Optional.of(c);
				})
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toSet());
		}
	}

	@Override
	protected void configure() {
		OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<UnaryOperator<Set<ForkBuilder>>>() { })
			.setBinding()
			.to(ForkOverwrite.class);
	}
}
