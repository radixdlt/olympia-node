package com.radixdlt.client.core.atoms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;

public class ShardsTest {
	@Test
	public void when_checking_if_included_shard_is_in_singleton_shard_space__intersection_can_be_detected() {
		long singletonShard = 1;
		Shards shards = Shards.range(singletonShard, singletonShard);
		assertThat(shards.contains(singletonShard)).isTrue();
		assertThat(shards.contains(singletonShard + 1)).isFalse();
		assertThat(shards.intersects(
			Arrays.asList(singletonShard, singletonShard + 1, singletonShard + 2, singletonShard + 3, singletonShard + 4))
		).isTrue();
		assertThat(shards.intersects(
			Arrays.asList(singletonShard + 1, singletonShard + 2, singletonShard + 3, singletonShard + 4))
		).isFalse();
	}

	@Test
	public void when_checking_if_included_shard_is_in_non_wrapped_shard_space__intersection_can_be_detected() {
		long lowShard = 0;
		long highShard = 2;
		Shards shards = Shards.range(lowShard, highShard);
		assertThat(shards.contains(lowShard + 1)).isTrue();
		assertThat(shards.contains(highShard + 1)).isFalse();
		assertThat(shards.intersects(
			Arrays.asList(lowShard, lowShard + 1, lowShard + 2, lowShard + 3, lowShard + 4))
		).isTrue();
		assertThat(shards.intersects(
			Arrays.asList(highShard, highShard + 1, highShard + 2, highShard + 3, highShard + 4))
		).isTrue();
	}

	@Test
	public void when_checking_if_included_shard_is_in_wrapped_shard_space__intersection_can_be_detected() {
		long lowShard = Long.MAX_VALUE;
		long highShard = 1;
		Shards shards = Shards.range(lowShard, highShard);
		assertThat(shards.contains(lowShard + 1)).isTrue();
		assertThat(shards.contains(highShard + 1)).isFalse();
		assertThat(shards.intersects(
			Arrays.asList(lowShard, lowShard + 1, lowShard + 2, lowShard + 3, lowShard + 4))
		).isTrue();
		assertThat(shards.intersects(
			Arrays.asList(highShard, highShard + 1, highShard + 2, highShard + 3, highShard + 4))
		).isTrue();
	}
}