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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.collect.Maps;

/**
 * Event counting utility class.
 */
public final class SystemCountersImpl implements SystemCounters {
	private static final List<CounterType> COUNTER_LIST = List.of(CounterType.values());

	private final EnumMap<CounterType, AtomicLong> counters = new EnumMap<>(CounterType.class);
	private final String since;

	public SystemCountersImpl() {
		this(System.currentTimeMillis());
	}

	public SystemCountersImpl(long startTime) {
		COUNTER_LIST.stream()
			.filter(counterType -> counterType != CounterType.TIME_DURATION)
			.forEach(counterType -> counters.put(counterType, new AtomicLong(0)));

		//This one is special, kinda "self ticking"
		counters.put(CounterType.TIME_DURATION, new AtomicLong() {
			@Override
			public long longValue() {
				return System.currentTimeMillis() - startTime;
			}
		});

		since = Instant.ofEpochMilli(startTime).toString();
	}

	@Override
	public long increment(CounterType counterType) {
		return counters.get(counterType).incrementAndGet();
	}

	@Override
	public long add(CounterType counterType, long amount) {
		return counters.get(counterType).addAndGet(amount);
	}

	@Override
	public long set(CounterType counterType, long value) {
		return counters.get(counterType).getAndSet(value);
	}

	@Override
	public long get(CounterType counterType) {
		return counters.get(counterType).longValue();
	}

	@Override
	public void setAll(Map<CounterType, Long> newValues) {
		// Note that this only prevents read tearing
		// Lost updates are still possible
		synchronized (counters) {
			for (Map.Entry<CounterType, Long> e : newValues.entrySet()) {
				counters.get(e.getKey()).set(e.getValue());
			}
		}
	}

	@Override
	public Map<String, Object> toMap() {
		var output = Maps.<String, Object>newTreeMap();

		synchronized (counters) {
			COUNTER_LIST.forEach(counter -> addValue(output, makePath(counter.jsonPath()), get(counter)));
		}

		addValue(output, makePath("time.since"), since);

		return output;
	}

	@SuppressWarnings("unchecked")
	private void addValue(Map<String, Object> values, String[] path, Object value) {
		for (int i = 0; i < path.length - 1; ++i) {
			// Needs exhaustive testing to ensure correctness.
			// Will fail if there is a counter called foo.bar and a counter called foo.bar.baz.
			values = (Map<String, Object>) values.computeIfAbsent(path[i], k -> Maps.newTreeMap());
		}
		values.put(path[path.length - 1], value);
	}

	private String[] makePath(String jsonPath) {
		return jsonPath.split("\\.");
	}

	@Override
	public String toString() {
		return String.format("SystemCountersImpl[%s]", toMap());
	}
}
