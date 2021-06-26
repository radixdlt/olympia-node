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
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.OptionalBinder;

import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class ForkOverwritesWithShorterEpochsModule extends AbstractModule {
	private final RERulesConfig config;

	public ForkOverwritesWithShorterEpochsModule(RERulesConfig config) {
		this.config = config;
	}

	@Override
	protected void configure() {
		var epoch = new AtomicLong(0);
		OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<UnaryOperator<Set<ForkConfig>>>() { })
			.setBinding()
			.toInstance(s ->
				s.stream()
					.sorted(Comparator.comparingLong(ForkConfig::getEpoch))
					.map(c -> new ForkConfig(
						epoch.getAndAdd(5),
						c.getName(),
						c.getVersion(),
						config
					))
					.collect(Collectors.toSet())
			);
	}
}
