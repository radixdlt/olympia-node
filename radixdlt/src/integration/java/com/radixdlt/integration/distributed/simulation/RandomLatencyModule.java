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
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.radixdlt.integration.distributed.simulation.network.RandomLatencyProvider;
import com.radixdlt.integration.distributed.simulation.network.SimulationNetwork.LatencyProvider;

public class RandomLatencyModule extends AbstractModule {
	private final int minLatency;
	private final int maxLatency;

	public RandomLatencyModule(int minLatency, int maxLatency) {
		this.minLatency = minLatency;
		this.maxLatency = maxLatency;
	}

	@Provides
	@Singleton
	@Named("base")
	LatencyProvider base() {
		return new RandomLatencyProvider(minLatency, maxLatency);
	}
}
