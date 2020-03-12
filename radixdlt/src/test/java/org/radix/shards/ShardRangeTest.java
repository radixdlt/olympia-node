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

package org.radix.shards;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class ShardRangeTest {

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(ShardRange.class)
			.verify();
	}

	@Test
	public void testShardRangeConstructor0() {
		ShardRange test = new ShardRange();
		assertEquals(0L, test.getHigh());
		assertEquals(0L, test.getLow());
		assertEquals(0L, test.getSpan());
	}

	@Test(expected = IllegalStateException.class)
	public void testShardRangeConstructor2Fail() {
		assertNull(new ShardRange(1, 0));
	}

	@Test
	public void testShardRangeConstructor2() {
		ShardRange test = new ShardRange(0, 1);
		assertEquals(0L, test.getLow());
		assertEquals(1L, test.getHigh());
		assertEquals(1L, test.getSpan());
	}

	@Test
	public void testIntersects() {
		// 0L, 0L range intersects with nothing
		ShardRange test0 = new ShardRange(0, 0);
		assertFalse(test0.intersects(Long.MIN_VALUE));
		assertFalse(test0.intersects(0L));
		assertFalse(test0.intersects(Long.MAX_VALUE));

		ShardRange test1 = new ShardRange(0, 1);
		assertFalse(test1.intersects(Long.MIN_VALUE));
		assertFalse(test1.intersects(-1));
		assertTrue(test1.intersects(0L));
		assertTrue(test1.intersects(1L));
		assertFalse(test1.intersects(2L));
		assertFalse(test1.intersects(Long.MAX_VALUE));

		ShardRange test2 = new ShardRange(1, 1);
		assertFalse(test2.intersects(Long.MIN_VALUE));
		assertFalse(test2.intersects(0L));
		assertTrue(test2.intersects(1L));
		assertFalse(test2.intersects(2L));
		assertFalse(test2.intersects(Long.MAX_VALUE));
	}

	@Test
	public void testOverlaps() {
		// 0L, 0L range intersects with nothing
		ShardRange test0 = new ShardRange(0, 0);
		assertFalse(test0.intersects(test0));

		ShardRange test1 = new ShardRange(0, 2);
		assertFalse(test1.intersects(new ShardRange(Long.MIN_VALUE, -1))); // Entirely on left
		assertTrue(test1.intersects(new ShardRange(Long.MIN_VALUE, 0))); // Touching left
		assertTrue(test1.intersects(new ShardRange(Long.MIN_VALUE, 1))); // Overlapping left
		assertTrue(test1.intersects(new ShardRange(Long.MIN_VALUE, 2))); // Overlapping left full
		assertTrue(test1.intersects(new ShardRange(0, 1))); // Touching left inside
		assertTrue(test1.intersects(test1)); // Exact match
		assertTrue(test1.intersects(new ShardRange(1, 1))); // Completely inside
		assertTrue(test1.intersects(new ShardRange(Long.MIN_VALUE, Long.MAX_VALUE))); // Complete overlap
		assertTrue(test1.intersects(new ShardRange(1, 2))); // Touching right inside
		assertTrue(test1.intersects(new ShardRange(0, Long.MAX_VALUE))); // Overlapping right full
		assertTrue(test1.intersects(new ShardRange(1, Long.MAX_VALUE))); // Overlapping right
		assertTrue(test1.intersects(new ShardRange(2, Long.MAX_VALUE))); // Touching right
		assertFalse(test1.intersects(new ShardRange(3, Long.MAX_VALUE))); // Entirely on right

		ShardRange test2 = new ShardRange(1, 3);
		assertFalse(test2.intersects(new ShardRange(Long.MIN_VALUE, 0))); // Entirely on left
		assertTrue(test2.intersects(new ShardRange(Long.MIN_VALUE, 1))); // Touching left
		assertTrue(test2.intersects(new ShardRange(Long.MIN_VALUE, 2))); // Overlapping left
		assertTrue(test2.intersects(new ShardRange(Long.MIN_VALUE, 3))); // Overlapping left full
		assertTrue(test2.intersects(new ShardRange(1, 2))); // Touching left inside
		assertTrue(test2.intersects(test2)); // Exact match
		assertTrue(test2.intersects(new ShardRange(2, 2))); // Completely inside
		assertTrue(test2.intersects(new ShardRange(Long.MIN_VALUE, Long.MAX_VALUE))); // Complete overlap
		assertTrue(test2.intersects(new ShardRange(2, 3))); // Touching right inside
		assertTrue(test2.intersects(new ShardRange(1, Long.MAX_VALUE))); // Overlapping right full
		assertTrue(test2.intersects(new ShardRange(1, Long.MAX_VALUE))); // Overlapping right
		assertTrue(test2.intersects(new ShardRange(3, Long.MAX_VALUE))); // Touching right
		assertFalse(test2.intersects(new ShardRange(4, Long.MAX_VALUE))); // Entirely on right
	}

	@Test
	public void testToString() {
		String test = new ShardRange(123456, 234567).toString();
		assertThat(test, containsString(ShardRange.class.getSimpleName()));
	}
}
