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

package com.radixdlt;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.radixdlt.chaos.BFTNodeMessageFlooder;
import com.radixdlt.chaos.ChaosRunner;
import com.radixdlt.chaos.MessageFloodUpdate;
import com.radixdlt.chaos.ScheduledMessageFlood;
import com.radixdlt.environment.EventProcessor;

/**
 * Module for chaos type functions
 */
public final class ChaosModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder<String, ModuleRunner> moduleRunners = MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class);
		moduleRunners.addBinding("chaos").to(ChaosRunner.class).in(Scopes.SINGLETON);
		bind(BFTNodeMessageFlooder.class).in(Scopes.SINGLETON);
	}

	@Provides
	public EventProcessor<MessageFloodUpdate> messageFloodUpdateEventProcessor(BFTNodeMessageFlooder bftNodeMessageFlooder) {
		return bftNodeMessageFlooder.messageFloodUpdateProcessor();
	}

	@Provides
	public EventProcessor<ScheduledMessageFlood> scheduledMessageFloodEventProcessor(BFTNodeMessageFlooder bftNodeMessageFlooder) {
		return bftNodeMessageFlooder.scheduledMessageFloodProcessor();
	}
}
