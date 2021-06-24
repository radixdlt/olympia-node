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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public final class ForksModule extends AbstractModule {
	@Override
	protected void configure() {
		OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<UnaryOperator<Set<ForkConfig>>>() { });
		install(new BetanetForkTxnRulesModule());
	}

	@Provides
	@Singleton
	private Forks forks(
		Set<ForkConfig> forkConfigs,
		Optional<UnaryOperator<Set<ForkConfig>>> transformer,
		Map<String, Function<RERulesConfig, RERules>> rules
	) {
		var transformed = transformer.map(o -> o.apply(forkConfigs))
			.orElse(forkConfigs);

		var map = new TreeMap<>(
			transformed.stream()
				.collect(Collectors.toMap(
					ForkConfig::getEpoch,
					e -> rules.get(e.getName()).apply(e.getConfig())
				))
		);

		return new Forks(map);
	}
}
