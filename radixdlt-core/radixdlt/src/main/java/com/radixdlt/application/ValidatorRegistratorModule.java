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

package com.radixdlt.application;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.chaos.mempoolfiller.InMemoryWalletReducer;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.Pair;

import java.util.Random;

/**
 * Manages node validator registration.
 */
public final class ValidatorRegistratorModule extends AbstractModule {
	@Override
	public void configure() {
		bind(ValidatorRegistrator.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(ValidatorRegistration.class);
	}

	@ProvidesIntoSet
	private Pair<String, StateReducer<?, ?>> nodeWallet(
		@NativeToken RRI tokenRRI,
		@Self RadixAddress self,
		Random random
	) {
		return Pair.of("self", new InMemoryWalletReducer(tokenRRI, self, random));
	}

	@ProvidesIntoSet
	private StateReducer<?, ?> validatorState(@Self RadixAddress self) {
		return new ValidatorStateReducer(self);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> mempoolFillerUpdateProcessor(ValidatorRegistrator validatorRegistrator) {
		return new EventProcessorOnRunner<>(
			"application",
			ValidatorRegistration.class,
			validatorRegistrator.validatorRegistrationEventProcessor()
		);
	}
}
