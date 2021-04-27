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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;

import java.util.EnumMap;
import java.util.List;

import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoService {
	private static final long DEFAULT_COLLECTING_INTERVAL = 1000L; // 1 second
	private static final long DEFAULT_AVERAGING_FACTOR = 10L; // averaging time in multiples of collecting interval

	private static final List<CounterType> COUNTERS = List.of(
		CounterType.ELAPSED_BDB_LEDGER_COMMIT,
		CounterType.MEMPOOL_PROPOSED_TRANSACTION
	);

	private final SystemCounters systemCounters;
	private final ScheduledEventDispatcher<ScheduledStatsCollecting> scheduledStatsCollecting;
	private final EnumMap<CounterType, ValueHolder> statistics = new EnumMap<>(CounterType.class);

	@Inject
	public NetworkInfoService(
		SystemCounters systemCounters,
		ScheduledEventDispatcher<ScheduledStatsCollecting> scheduledStatsCollecting
	) {
		this.scheduledStatsCollecting = scheduledStatsCollecting;
		this.systemCounters = systemCounters;
		COUNTERS.forEach(cnt -> statistics.put(cnt, new ValueHolder(DEFAULT_AVERAGING_FACTOR)));

		scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
	}

	public long throughput() {
		return statistics.get(CounterType.ELAPSED_BDB_LEDGER_COMMIT).average();
	}

	public long demand() {
		return statistics.get(CounterType.MEMPOOL_PROPOSED_TRANSACTION).average();
	}

	public EventProcessor<ScheduledStatsCollecting> updateStats() {
		return flush -> {
			collectStats();
			scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
		};
	}

	@VisibleForTesting
	void collectStats() {
		COUNTERS.forEach(cnt -> statistics.compute(cnt, this::updateCounter));
	}

	private ValueHolder updateCounter(CounterType counterType, ValueHolder holder) {
		return counterType != null ? holder.update(systemCounters.get(counterType)) : null;
	}

	private static class ValueHolder {
		private final MovingAverage calculator;
		private long lastValue;

		public ValueHolder(long averagingFactor) {
			this.calculator = MovingAverage.create(averagingFactor);
		}

		public ValueHolder update(long newValue) {
			calculator.update(newValue - lastValue);
			lastValue = newValue;
			return this;
		}

		public long average() {
			return calculator.asLong();
		}
	}
}
