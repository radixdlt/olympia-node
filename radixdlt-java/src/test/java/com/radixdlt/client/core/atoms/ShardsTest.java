package com.radixdlt.client.core.atoms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.Test;

public class ShardsTest {
	@Test
	public void inclusionTest() {
		Shards shards = Shards.range(1, 1);
		assertThat(shards.contains(1)).isTrue();
		assertThat(shards.contains(2)).isFalse();
		assertThat(shards.intersects(Arrays.asList(1L, 2L, 3L, 4L, 5L))).isTrue();
		assertThat(shards.intersects(Arrays.asList(2L, 3L, 4L, 5L))).isFalse();
	}

	@Test
	public void inclusionNonWrappedTest() {
		Shards shards = Shards.range(0, 2);
		assertThat(shards.contains(1)).isTrue();
		assertThat(shards.contains(3)).isFalse();
		assertThat(shards.intersects(Arrays.asList(1L, 2L, 3L, 4L, 5L))).isTrue();
		assertThat(shards.intersects(Arrays.asList(3L, 4L, 5L))).isFalse();
	}

	@Test
	public void inclusionWrappedTest() {
		Shards shards = Shards.range(Long.MAX_VALUE, 1);
		assertThat(shards.contains(0)).isTrue();
		assertThat(shards.contains(2)).isFalse();
		assertThat(shards.intersects(Arrays.asList(0L, 2L, 3L, 4L, 5L))).isTrue();
		assertThat(shards.intersects(Arrays.asList(3L, 4L, 5L))).isFalse();
	}
}