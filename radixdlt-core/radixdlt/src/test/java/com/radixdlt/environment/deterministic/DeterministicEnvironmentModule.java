/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.environment.deterministic;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.radixdlt.consensus.liveness.EpochLocalTimeoutOccurrence;
import com.radixdlt.consensus.bft.BFTNode;
import com.radixdlt.consensus.bft.Self;
import com.radixdlt.consensus.epoch.EpochViewUpdate;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.Environment;
import com.radixdlt.environment.EventProcessorOnDispatch;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.RemoteEventProcessorOnRunner;
import com.radixdlt.environment.deterministic.network.ControlledSender;

/**
 * Module that supplies network senders, as well as some other assorted
 * objects used to connect modules in the system.
 */
public class DeterministicEnvironmentModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(SystemCounters.class).to(SystemCountersImpl.class).in(Scopes.SINGLETON);
		bind(Environment.class).to(ControlledSender.class);

		bind(new TypeLiteral<DeterministicSavedLastEvent<EpochLocalTimeoutOccurrence>>() { })
			.toInstance(new DeterministicSavedLastEvent<>(EpochLocalTimeoutOccurrence.class));
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { })
			.addBinding().toProvider(new TypeLiteral<DeterministicSavedLastEvent<EpochLocalTimeoutOccurrence>>() { });

		bind(new TypeLiteral<DeterministicSavedLastEvent<EpochViewUpdate>>() { })
			.toInstance(new DeterministicSavedLastEvent<>(EpochViewUpdate.class));
		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnDispatch<?>>() { })
			.addBinding().toProvider(new TypeLiteral<DeterministicSavedLastEvent<EpochViewUpdate>>() { });

		Multibinder.newSetBinder(binder(), new TypeLiteral<EventProcessorOnRunner<?>>() { });
		Multibinder.newSetBinder(binder(), new TypeLiteral<RemoteEventProcessorOnRunner<?>>() { });
	}

	@Provides
	@Singleton
	ControlledSender sender(@Self BFTNode self, ControlledSenderFactory senderFactory) {
		return senderFactory.create(self);
	}
}
