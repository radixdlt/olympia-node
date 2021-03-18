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
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;

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
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> mempoolFillerUpdateProcessor(MempoolFiller mempoolFiller) {
		return new EventProcessorOnRunner<>("chaos", MempoolFillerUpdate.class, mempoolFiller.mempoolFillerUpdateEventProcessor());
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> scheduledMessageFloodEventProcessor(MempoolFiller mempoolFiller) {
		return new EventProcessorOnRunner<>("chaos", ScheduledMempoolFill.class, mempoolFiller.scheduledMempoolFillEventProcessor());
	}
}
