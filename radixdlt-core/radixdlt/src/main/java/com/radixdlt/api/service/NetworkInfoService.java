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

package com.radixdlt.api.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.radixdlt.api.data.NodeStatus;
import com.radixdlt.counters.SystemCounters;
import com.radixdlt.environment.EventProcessor;
import com.radixdlt.environment.ScheduledEventDispatcher;

import java.util.EnumMap;

import static com.radixdlt.api.data.NodeStatus.BOOTING;
import static com.radixdlt.api.data.NodeStatus.OUT_OF_SYNC;
import static com.radixdlt.api.data.NodeStatus.STALLED;
import static com.radixdlt.api.data.NodeStatus.SYNCING;
import static com.radixdlt.api.data.NodeStatus.UP;
import static com.radixdlt.api.service.NetworkInfoService.ValueHolder.Type.ABSOLUTE;
import static com.radixdlt.api.service.NetworkInfoService.ValueHolder.Type.INCREMENTAL;
import static com.radixdlt.counters.SystemCounters.CounterType;

public class NetworkInfoService {
	private static final Logger log = LogManager.getLogger();

	private static final long THRESHOLD = 3;                      // Maximum difference between ledger and target
	private static final long DEFAULT_COLLECTING_INTERVAL = 1000L; // 1 second
	private static final long DEFAULT_AVERAGING_FACTOR = 10L;     // averaging time in multiples of collecting interval
	private static final long STATUS_AVERAGING_FACTOR = 3L;       // averaging time in multiples of collecting interval

	public static final CounterType THROUGHPUT_KEY = CounterType.COUNT_BDB_LEDGER_COMMIT;
	public static final CounterType DEMAND_KEY = CounterType.MEMPOOL_COUNT;
	public static final CounterType LEDGER_KEY = CounterType.LEDGER_STATE_VERSION;
	public static final CounterType TARGET_KEY = CounterType.SYNC_TARGET_STATE_VERSION;

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
		statistics.put(LEDGER_KEY, new ValueHolder(STATUS_AVERAGING_FACTOR, ABSOLUTE));
		statistics.put(TARGET_KEY, new ValueHolder(STATUS_AVERAGING_FACTOR, ABSOLUTE));

		scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
	}

	public long throughput() {
		return statistics.get(THROUGHPUT_KEY).average();
	}

	public long demand() {
		return statistics.get(DEMAND_KEY).average();
	}

	public NodeStatus nodeStatus() {
		if (statistics.get(LEDGER_KEY).lastValue() == 0) {
			// Initial status, consensus not started yet
			return BOOTING;
		}

		if (statistics.get(LEDGER_KEY).isGrowing()) {
			// Ledger state version is increasing, so we're completely synced up or catching up
			return ledgerIsCloseToTarget() ? UP : SYNCING;
		}

		// Ledger is not growing, either node stall or whole network is down or not reachable
		return statistics.get(TARGET_KEY).isGrowing() ? STALLED : OUT_OF_SYNC;
	}

	public EventProcessor<ScheduledStatsCollecting> updateStats() {
		return flush -> {
			collectStats();
			scheduledStatsCollecting.dispatch(ScheduledStatsCollecting.create(), DEFAULT_COLLECTING_INTERVAL);
		};
	}

	private void collectStats() {
		statistics.forEach((key, value) -> statistics.compute(key, this::updateCounter));
	}

	private ValueHolder updateCounter(CounterType counterType, ValueHolder holder) {
		return counterType != null ? holder.update(systemCounters.get(counterType)) : null;
	}

	private boolean ledgerIsCloseToTarget() {
		return (statistics.get(TARGET_KEY).lastValue() - statistics.get(LEDGER_KEY).lastValue()) < THRESHOLD;
	}

	static class ValueHolder {
		private final MovingAverage calculator;
		private final MovingAverage deltaCalculator;
		private final Type type;
		private long lastValue;

		public enum Type {
			ABSOLUTE,
			INCREMENTAL
		}

		private ValueHolder(long averagingFactor, Type type) {
			calculator = MovingAverage.create(averagingFactor);
			deltaCalculator = MovingAverage.create(averagingFactor);
			this.type = type;
		}

		public ValueHolder update(long newValue) {
			var lastDelta = newValue - lastValue;

			lastValue = newValue;
			deltaCalculator.update(lastDelta);

			if (type == ABSOLUTE) {
				calculator.update(newValue);
			} else {
				calculator.update(lastDelta);
			}

			return this;
		}

		public long average() {
			return calculator.asLong();
		}

		public long lastValue() {
			return lastValue;
		}

		public boolean isGrowing() {
			return deltaCalculator.asDouble() > 0.1;
		}
	}
}
