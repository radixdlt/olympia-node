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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoServiceTest {
	@SuppressWarnings("unchecked")
	private final ScheduledEventDispatcher<ScheduledStatsCollecting> dispatcher = mock(ScheduledEventDispatcher.class);
	private final SystemCounters systemCounters = new SystemCountersImpl();
	private final NetworkInfoService networkInfoService = new NetworkInfoService(systemCounters, dispatcher);

	@Test
	public void testDemand() {
		assertEquals(0, networkInfoService.demand());

		systemCounters.add(CounterType.MEMPOOL_PROPOSED_TRANSACTION, 1L);
		updateStats(10, CounterType.MEMPOOL_PROPOSED_TRANSACTION);

		assertEquals(1, networkInfoService.demand());
	}

	@Test
	public void testThroughput() {
		assertEquals(0, networkInfoService.throughput());

		systemCounters.add(CounterType.ELAPSED_BDB_LEDGER_COMMIT, 1L);
		updateStats(10, CounterType.ELAPSED_BDB_LEDGER_COMMIT);

		assertEquals(1, networkInfoService.throughput());
	}

	private void updateStats(int times, CounterType counterType) {
		for (int i = 0; i < times; i++) {
			systemCounters.increment(counterType);
			networkInfoService.updateStats().process(ScheduledStatsCollecting.create());
		}
	}
}
