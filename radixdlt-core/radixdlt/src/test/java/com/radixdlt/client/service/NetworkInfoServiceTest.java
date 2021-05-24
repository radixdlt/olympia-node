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
package com.radixdlt.client.service;

import org.junit.Test;

import com.radixdlt.counters.SystemCounters;
import com.radixdlt.counters.SystemCountersImpl;
import com.radixdlt.environment.ScheduledEventDispatcher;

import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import static com.radixdlt.client.service.NetworkInfoService.DEMAND_KEY;
import static com.radixdlt.client.service.NetworkInfoService.THROUGHPUT_KEY;
import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoServiceTest {
	@SuppressWarnings("unchecked")
	private final ScheduledEventDispatcher<ScheduledStatsCollecting> dispatcher = mock(ScheduledEventDispatcher.class);
	private final SystemCounters systemCounters = new SystemCountersImpl();
	private final NetworkInfoService networkInfoService = new NetworkInfoService(systemCounters, dispatcher);

	@Test
	public void testDemand() {
		assertEquals(0, networkInfoService.demand());

		systemCounters.set(DEMAND_KEY, 2L);
		updateStats(5, DEMAND_KEY, false);

		systemCounters.set(DEMAND_KEY, 0L);
		updateStats(5, DEMAND_KEY, false);

		assertEquals(1, networkInfoService.demand());
	}

	@Test
	public void testThroughput() {
		assertEquals(0, networkInfoService.throughput());

		systemCounters.add(THROUGHPUT_KEY, 1L);
		updateStats(10, THROUGHPUT_KEY, true);

		assertEquals(1, networkInfoService.throughput());
	}

	private void updateStats(int times, CounterType counterType, boolean increment) {
		IntStream.range(0, times).forEach(__ -> {
			if (increment) {
				systemCounters.increment(counterType);
			}
			networkInfoService.updateStats().process(ScheduledStatsCollecting.create());
		});
	}
}
