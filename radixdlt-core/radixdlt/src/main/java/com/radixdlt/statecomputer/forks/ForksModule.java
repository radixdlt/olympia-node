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

import java.util.Collection;
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
		install(new MainnetForkConfigsModule());
	}

	@Provides
	@Singleton
	private Forks forks(TreeMap<Long, ForkConfig> forkConfigs) {
		return new Forks(asTreeMap(forkConfigs.values(), e -> e.getVersion().create(e.getConfig())));
	}

	@Provides
	@Singleton
	private TreeMap<Long, ForkConfig> forkConfigMap(
		Set<ForkConfig> forkConfigs,
		Optional<UnaryOperator<Set<ForkConfig>>> transformer
	) {
		return asTreeMap(
			transformer.map(o -> o.apply(forkConfigs)).orElse(forkConfigs),
			Function.identity()
		);
	}

	private static <T> TreeMap<Long, T> asTreeMap(Collection<ForkConfig> input, Function<ForkConfig, T> mapper) {
		return new TreeMap<>(
			input.stream().collect(Collectors.toMap(ForkConfig::getEpoch, mapper))
		);
	}
}
