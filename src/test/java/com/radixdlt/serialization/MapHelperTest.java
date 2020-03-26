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

package com.radixdlt.serialization;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

/**
 * Basic tests for {@link MapHelper}.
 */
public class MapHelperTest {

	@Test
	public void testMapOf1() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L);
		assertEquals(map, ImmutableMap.of("k1", 1L));
	}

	@Test
	public void testMapOf2() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L);
		assertEquals(map, ImmutableMap.of("k1", 1L, "k2", 2L));
	}

	@Test
	public void testMapOf3() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L, "k3", 3L);
		assertEquals(map, ImmutableMap.of("k1", 1L, "k2", 2L, "k3", 3L));
	}

	@Test
	public void testMapOf4() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L);
		assertEquals(map, ImmutableMap.of("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L));
	}

	@Test
	public void testMapOf5() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L, "k5", 5L);
		assertEquals(map, ImmutableMap.of("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L, "k5", 5L));
	}

	@Test
	public void testMapOf6() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L, "k5", 5L, "k6", 6L);
		ImmutableMap<String, Long> imap = new ImmutableMap.Builder<String, Long>()
				.put("k1", 1L)
				.put("k2", 2L)
				.put("k3", 3L)
				.put("k4", 4L)
				.put("k5", 5L)
				.put("k6", 6L)
				.build();
		assertEquals(map, imap);
	}

	@Test
	public void testMapOf7() {
		Map<String, Object> map = MapHelper.mapOf("k1", 1L, "k2", 2L, "k3", 3L, "k4", 4L, "k5", 5L, "k6", 6L, "k7", 7L);
		ImmutableMap<String, Long> imap = new ImmutableMap.Builder<String, Long>()
				.put("k1", 1L)
				.put("k2", 2L)
				.put("k3", 3L)
				.put("k4", 4L)
				.put("k5", 5L)
				.put("k6", 6L)
				.put("k7", 7L)
				.build();
		assertEquals(map, imap);
	}
}
