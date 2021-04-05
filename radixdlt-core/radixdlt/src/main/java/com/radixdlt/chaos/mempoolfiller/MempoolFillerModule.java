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
 */

package com.radixdlt.chaos.mempoolfiller;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.engine.StateReducer;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.Runners;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.fees.NativeToken;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;

/**
 * Module responsible for the mempool filler chaos attack
 */
public final class MempoolFillerModule extends AbstractModule {
	@Override
	public void configure() {
		bind(MempoolFiller.class).in(Scopes.SINGLETON);
		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
				.permitDuplicates();
		eventBinder.addBinding().toInstance(MempoolFillerUpdate.class);
		eventBinder.addBinding().toInstance(ScheduledMempoolFill.class);

		Multibinder.newSetBinder(binder(), new TypeLiteral<StateReducer<?, ?>>() { })
			.addBinding().to(ParticleCounter.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> mempoolFillerUpdateProcessor(MempoolFiller mempoolFiller) {
		return new EventProcessorOnRunner<>(Runners.CHAOS, MempoolFillerUpdate.class, mempoolFiller.mempoolFillerUpdateEventProcessor());
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> scheduledMessageFloodEventProcessor(MempoolFiller mempoolFiller) {
		return new EventProcessorOnRunner<>(Runners.CHAOS, ScheduledMempoolFill.class, mempoolFiller.scheduledMempoolFillEventProcessor());
	}

	@ProvidesIntoSet
	private StateReducer<?, ?> particleCounter(@NativeToken RRI tokenRRI, @Self RadixAddress self) {
		return new ParticleCounter(tokenRRI, self);
	}
}
