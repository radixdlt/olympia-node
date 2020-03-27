package com.radixdlt.consensus;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.radixdlt.consensus.Counters.CounterType;
import org.junit.Test;

public class CountersTest {
	@Test
	public void when_get_count__then_count_should_be_0() {
		Counters counters = new Counters();
		assertThat(counters.getCount(CounterType.TIMEOUT)).isEqualTo(0);
	}

	@Test
	public void when_increment__then_count_should_be_1() {
		Counters counters = new Counters();
		counters.increment(CounterType.TIMEOUT);
		assertThat(counters.getCount(CounterType.TIMEOUT)).isEqualTo(1);
	}
}