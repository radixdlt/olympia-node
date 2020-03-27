package com.radixdlt.consensus;

import java.util.EnumMap;
import java.util.Map;

/**
 * Consensus event counting utility class.
 * Class is NOT thread-safe so must be run in the same thread or with correct locking.
 */
public final class Counters {
	public enum CounterType {
		TIMEOUT
	}

	private Map<CounterType, Long> counters = new EnumMap<>(CounterType.class);

	public void increment(CounterType counterType) {
		counters.merge(counterType, 1L, Long::sum);
	}

	public Long getCount(CounterType counterType) {
		return counters.getOrDefault(counterType, 0L);
	}
}
