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
import com.radixdlt.atommodel.tokens.Amount;

import java.util.Comparator;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * For testing only, only tests the latest state computer configuration
 */
public class RadixEngineForksLatestOnlyModule extends AbstractModule {
	private final RERulesConfig config;

	public RadixEngineForksLatestOnlyModule(RERulesConfig config) {
		this.config = config;
	}

	public RadixEngineForksLatestOnlyModule() {
		this(
			new RERulesConfig(
				false,
				10,
				2,
				Amount.ofTokens(10),
				Amount.ofTokens(10)
			)
		);
	}

	@Override
	protected void configure() {
		OptionalBinder.newOptionalBinder(binder(), new TypeLiteral<UnaryOperator<Set<ForkConfig>>>() { })
			.setBinding()
			.toInstance(m ->
				Set.of(m.stream()
					.max(Comparator.comparingLong(ForkConfig::getEpoch))
					.map(f -> new ForkConfig(0L, f.getName(), f.getVersion(), config))
					.orElseThrow())
			);
	}
}
