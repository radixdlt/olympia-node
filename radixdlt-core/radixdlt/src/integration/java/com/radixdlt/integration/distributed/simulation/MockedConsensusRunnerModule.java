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

package com.radixdlt.integration.distributed.simulation;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.ModuleRunner;
import com.radixdlt.consensus.BFTEventProcessor;
import com.radixdlt.consensus.bft.ViewUpdate;
import com.radixdlt.consensus.liveness.ScheduledLocalTimeout;
import com.radixdlt.consensus.sync.BFTSync;
import com.radixdlt.consensus.sync.VertexRequestTimeout;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.integration.distributed.BFTRunner;
import com.radixdlt.ledger.LedgerUpdate;

public class MockedConsensusRunnerModule extends AbstractModule {
	@Override
	public void configure() {
		MapBinder<String, ModuleRunner> moduleRunners = MapBinder.newMapBinder(binder(), String.class, ModuleRunner.class);
		moduleRunners.addBinding(Runners.CONSENSUS).to(BFTRunner.class).in(Scopes.SINGLETON);

		var eventBinder = Multibinder.newSetBinder(binder(), new TypeLiteral<Class<?>>() { }, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(ScheduledLocalTimeout.class);
		eventBinder.addBinding().toInstance(VertexRequestTimeout.class);
		eventBinder.addBinding().toInstance(ViewUpdate.class);
		eventBinder.addBinding().toInstance(LedgerUpdate.class);
	}

	@ProvidesIntoSet
	private EventProcessor<ScheduledLocalTimeout> timeoutProcessor(BFTEventProcessor processor) {
		return processor::processLocalTimeout;
	}

	@ProvidesIntoSet
	public EventProcessor<VertexRequestTimeout> bftSyncTimeoutProcessor(BFTSync bftSync) {
		return bftSync.vertexRequestTimeoutEventProcessor();
	}

	@ProvidesIntoSet
	private EventProcessor<ViewUpdate> viewUpdateProcessor(BFTEventProcessor processor) {
		return processor::processViewUpdate;
	}
}
