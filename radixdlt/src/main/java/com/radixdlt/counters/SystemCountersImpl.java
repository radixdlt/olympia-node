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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.Maps;

/**
 * Event counting utility class.
 */
public final class SystemCountersImpl implements SystemCounters {
	private final ConcurrentHashMap<CounterType, Long> counters = new ConcurrentHashMap<>();
	private final String since;

	public SystemCountersImpl() {
		this(System.currentTimeMillis());
	}

	public SystemCountersImpl(long startTime) {
		this.since = Instant.ofEpochMilli(startTime).toString();
	}

	@Override
	public long increment(CounterType counterType) {
		return add(counterType, 1L);
	}

	@Override
	public long add(CounterType counterType, long amount) {
		return counters.merge(counterType, amount, Long::sum);
	}

	@Override
	public long set(CounterType counterType, long value) {
		Long oldValue = counters.put(counterType, value);
		return oldValue == null ? 0L : oldValue.longValue();
	}

	@Override
	public long get(CounterType counterType) {
		return counters.getOrDefault(counterType, 0L);
	}

	@Override
	public Map<String, Object> toMap() {
		Map<String, Object> output = Maps.newTreeMap();
		for (CounterType counter : CounterType.values()) {
			long value = get(counter);
			addValue(output, makePath(counter), value);
		}
		output.put("since", since);
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
		values.put(path[path.length - 1], Long.valueOf(value));
	}

	private String[] makePath(CounterType counter) {
		return counter.jsonPath().split("\\.");
	}

	@Override
	public String toString() {
		return String.format("SystemCountersImpl[%s]", toMap());
	}
}
