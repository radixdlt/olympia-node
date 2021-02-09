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

package com.radixdlt.chaos;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.radixdlt.ModuleRunner;
import com.radixdlt.chaos.messageflooder.MessageFlooderModule;
import com.radixdlt.chaos.messageflooder.MessageFlooderUpdate;
import com.radixdlt.chaos.messageflooder.ScheduledMessageFlood;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.rx.ModuleRunnerImpl;
import io.reactivex.rxjava3.core.Observable;

/**
 * Module for chaos type functions
 */
public final class ChaosModule extends AbstractModule {
	@Override
	public void configure() {
		install(new MessageFlooderModule());
	}

	@ProvidesIntoMap
	@StringMapKey("chaos")
	public ModuleRunner chaosRunner(
		Observable<ScheduledMessageFlood> scheduledFloods,
		EventProcessor<ScheduledMessageFlood> scheduledFloodProcessor,
		Observable<MessageFlooderUpdate> messageFloodUpdates,
		EventProcessor<MessageFlooderUpdate> messageFloodUpdateProcessor
	) {
		return ModuleRunnerImpl.builder()
			.add(scheduledFloods, scheduledFloodProcessor)
			.add(messageFloodUpdates, messageFloodUpdateProcessor)
			.build("ChaosRunner");
	}
}
