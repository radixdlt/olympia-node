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

import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.counters.SystemCounters.CounterType;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertNotNull;

public class SystemCountersImplTest {
	@Test
	public void when_get_count__then_count_should_be_0() {
		SystemCounters counters = new SystemCountersImpl();
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(0L);
	}

	@Test
	public void when_increment__then_count_should_be_1() {
		SystemCounters counters = new SystemCountersImpl();
		counters.increment(CounterType.BFT_TIMEOUT);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(1L);
		counters.increment(CounterType.BFT_TIMEOUT);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(2L);
	}

	@Test
	public void when_add__then_count_should_be_added_value() {
		SystemCounters counters = new SystemCountersImpl();
		counters.add(CounterType.BFT_TIMEOUT, 1234);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(1234L);
		counters.add(CounterType.BFT_TIMEOUT, 4321);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(1234L + 4321L);
	}

	@Test
	public void when_set__then_count_should_be_1() {
		SystemCounters counters = new SystemCountersImpl();
		counters.set(CounterType.BFT_TIMEOUT, 1234);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(1234L);
		counters.set(CounterType.BFT_TIMEOUT, 4321);
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(4321L);
	}

	@Test
	public void when_set_all__then_count_should_be_correct() {
		SystemCounters counters = new SystemCountersImpl();
		counters.setAll(ImmutableMap.of(
			CounterType.BFT_TIMEOUT, 1234L,
			CounterType.BFT_PROPOSALS_MADE, 4567L
		));
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(1234L);
		assertThat(counters.get(CounterType.BFT_PROPOSALS_MADE)).isEqualTo(4567L);
		counters.setAll(ImmutableMap.of(
			CounterType.BFT_TIMEOUT, 2345L,
			CounterType.BFT_PROPOSALS_MADE, 5678L
		));
		assertThat(counters.get(CounterType.BFT_TIMEOUT)).isEqualTo(2345L);
		assertThat(counters.get(CounterType.BFT_PROPOSALS_MADE)).isEqualTo(5678L);
	}

	@Test
	public void when_tomap__then_values_correct() {
		SystemCounters counters = new SystemCountersImpl();
		for (CounterType value : CounterType.values()) {
			counters.set(value, ordinal(value));
		}
		// Ensure writeable
		Map<String, Object> m = new TreeMap<>(counters.toMap());
		assertNotNull(m.remove("time"));
		testMap("", m);
	}

	@Test
	public void sensible_tostring() {
		SystemCounters counters = new SystemCountersImpl();
		counters.set(CounterType.BFT_TIMEOUT, 1234);
		String s = counters.toString();
		assertThat(s).contains(SystemCountersImpl.class.getSimpleName());
		assertThat(s).contains("1234");
	}

	private int ordinal(CounterType value) {
		// Add one so that none are zero.
		// Zero is the default value, and this lets us catch the "not set" case.
		return value.ordinal() + 1;
	}

	private void testMap(String path, Map<String, Object> m) {
		for (Map.Entry<String, Object> entry : m.entrySet()) {
			String p = entry.getKey().toUpperCase();
			String newPath = path.isEmpty() ? p : path + "_" + p;
			Object o = entry.getValue();
			if (o instanceof Map<?, ?>) {
				@SuppressWarnings("unchecked")
				Map<String, Object> newm = (Map<String, Object>) o;
				testMap(newPath, newm);
			} else {
				String s = o.toString();
				CounterType ct = CounterType.valueOf(newPath);
				// Check that values in the map match the values we set above
				assertThat(Long.parseLong(s)).isEqualTo(ordinal(ct));
			}
		}
	}
}