/*
 * (C) Copyright 2020 Radix DLT Ltd
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

package com.radixdlt.counters;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Maps;

/**
 * Event counting utility class.
 */
public final class SystemCountersImpl implements SystemCounters {
	private final EnumMap<CounterType, AtomicLong> counters;
	private final long start;
	private final String since;
	private final Object lock = new Object();

	public SystemCountersImpl() {
		this(System.currentTimeMillis());
	}

	public SystemCountersImpl(long startTime) {
		this.counters = new EnumMap<>(CounterType.class);
		// Pre-populate the map here so that there is no need to mutate the structure
		for (CounterType ct : CounterType.values()) {
			this.counters.put(ct, new AtomicLong(0));
		}
		this.start = startTime;
		this.since = Instant.ofEpochMilli(startTime).toString();
	}

	@Override
	public long increment(CounterType counterType) {
		return this.counters.get(counterType).incrementAndGet();
	}

	@Override
	public long add(CounterType counterType, long amount) {
		return this.counters.get(counterType).addAndGet(amount);
	}

	@Override
	public long set(CounterType counterType, long value) {
		return this.counters.get(counterType).getAndSet(value);
	}

	@Override
	public long get(CounterType counterType) {
		return this.counters.get(counterType).get();
	}

	@Override
	public void setAll(Map<CounterType, Long> newValues) {
		// Note that this only prevents read tearing
		// Lost updates are still possible
		synchronized (this.lock) {
			for (Map.Entry<CounterType, Long> e : newValues.entrySet()) {
				this.counters.get(e.getKey()).set(e.getValue());
			}
		}
	}

	@Override
	public Map<String, Object> toMap() {
		Map<String, Object> output = Maps.newTreeMap();
		synchronized (this.lock) {
			for (CounterType counter : CounterType.values()) {
				long value = get(counter);
				addValue(output, makePath(counter), value);
			}
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> time = (Map<String, Object>) output.computeIfAbsent("time", k -> Maps.newTreeMap());
		time.put("since", since);
		time.put("duration", System.currentTimeMillis() - this.start);
		return output;
	}

	private void addValue(Map<String, Object> values, String[] path, long value) {
		for (int i = 0; i < path.length - 1; ++i) {
			@SuppressWarnings("unchecked")
			// Needs exhaustive testing to ensure correctness.
			// Will fail if there is a counter called foo.bar and a counter called foo.bar.baz.
			Map<String, Object> newValues = (Map<String, Object>) values.computeIfAbsent(path[i], k -> Maps.newTreeMap());
			values = newValues;
		}
		values.put(path[path.length - 1], value);
	}

	private String[] makePath(CounterType counter) {
		return counter.jsonPath().split("\\.");
	}

	@Override
	public String toString() {
		return String.format("SystemCountersImpl[%s]", toMap());
	}
}
