/*
 * (C) Copyright 2021 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.api.module;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.radixdlt.api.data.ScheduledQueueFlush;
import com.radixdlt.api.service.NetworkInfoService;
import com.radixdlt.api.service.ScheduledCacheCleanup;
import com.radixdlt.api.service.ScheduledStatsCollecting;
import com.radixdlt.environment.EventProcessorOnRunner;
import com.radixdlt.environment.LocalEvents;
import com.radixdlt.environment.Runners;
import com.radixdlt.mempool.MempoolAddFailure;
import com.radixdlt.statecomputer.AtomsRemovedFromMempool;
import com.radixdlt.statecomputer.REOutput;

public class CommonApiModule extends AbstractModule {
	@Override
	protected void configure() {
		var eventBinder = Multibinder
			.newSetBinder(binder(), new TypeLiteral<Class<?>>() {}, LocalEvents.class)
			.permitDuplicates();
		eventBinder.addBinding().toInstance(AtomsRemovedFromMempool.class);
		eventBinder.addBinding().toInstance(REOutput.class);
		eventBinder.addBinding().toInstance(MempoolAddFailure.class);
		eventBinder.addBinding().toInstance(ScheduledCacheCleanup.class);
		eventBinder.addBinding().toInstance(ScheduledQueueFlush.class);
		eventBinder.addBinding().toInstance(ScheduledStatsCollecting.class);

		bind(NetworkInfoService.class).in(Scopes.SINGLETON);
	}

	@ProvidesIntoSet
	public EventProcessorOnRunner<?> networkInfoService(NetworkInfoService networkInfoService) {
		return new EventProcessorOnRunner<>(
			Runners.APPLICATION,
			ScheduledStatsCollecting.class,
			networkInfoService.updateStats()
		);
	}
}
