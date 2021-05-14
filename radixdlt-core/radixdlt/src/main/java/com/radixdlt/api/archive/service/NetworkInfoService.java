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

package com.radixdlt.api.archive.service;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;

import java.util.EnumMap;

import static com.radixdlt.client.service.NetworkInfoService.ValueHolder.Type.ABSOLUTE;
import static com.radixdlt.client.service.NetworkInfoService.ValueHolder.Type.INCREMENTAL;
import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoService {
	private static final long DEFAULT_COLLECTING_INTERVAL = 1000L; // 1 second
	private static final long DEFAULT_AVERAGING_FACTOR = 10L; // averaging time in multiples of collecting interval
	public static final CounterType THROUGHPUT_KEY = CounterType.COUNT_BDB_LEDGER_COMMIT;
	public static final CounterType DEMAND_KEY = CounterType.MEMPOOL_COUNT;

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
		statistics.put(THROUGHPUT_KEY, new ValueHolder(DEFAULT_AVERAGING_FACTOR, INCREMENTAL));
		statistics.put(DEMAND_KEY, new ValueHolder(DEFAULT_AVERAGING_FACTOR, ABSOLUTE));

		scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
	}

	public long throughput() {
		return statistics.get(THROUGHPUT_KEY).average();
	}

	public long demand() {
		return statistics.get(DEMAND_KEY).average();
	}

	public EventProcessor<ScheduledStatsCollecting> updateStats() {
		return flush -> {
			collectStats();
			scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
		};
	}

	@VisibleForTesting
	void collectStats() {
		statistics.forEach((key, value) -> statistics.compute(key, this::updateCounter));
	}

	private ValueHolder updateCounter(CounterType counterType, ValueHolder holder) {
		return counterType != null ? holder.update(systemCounters.get(counterType)) : null;
	}

	static class ValueHolder {
		private final MovingAverage calculator;
		private final Type type;
		private long lastValue;

		public enum Type {
			ABSOLUTE,
			INCREMENTAL
		}

		private ValueHolder(long averagingFactor, Type type) {
			this.calculator = MovingAverage.create(averagingFactor);
			this.type = type;
		}

		public ValueHolder update(long newValue) {
			if (type == Type.ABSOLUTE) {
				calculator.update(newValue);
			} else {
				calculator.update(newValue - lastValue);
				lastValue = newValue;
			}
			return this;
		}

		public long average() {
			return calculator.asLong();
		}
	}
}
