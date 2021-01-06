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

package com.radixdlt.network.messaging;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimplePriorityBlockingQueueTest {

	static final class TestObject implements Comparable<TestObject> {
		final int i;

		TestObject(int i) {
			this.i = i;
		}

		@Override
		public int compareTo(TestObject o) {
			// All the same prio class
			return 0;
		}
	}

	@Test
	public void testOrdering() throws InterruptedException {
		// For this test, we need to ensure that we are not interrupted
		Thread.interrupted();

		SimplePriorityBlockingQueue<TestObject> test = new SimplePriorityBlockingQueue<>(100, TestObject::compareTo);

		final int testObjects = 1000;
		for (int i = 0; i < testObjects; ++i) {
			assertEquals(i, test.size());
			assertTrue(test.offer(new TestObject(i)));
		}

		for (int i = 0; i < testObjects; ++i) {
			assertEquals(i, test.take().i);
		}
	}

	@Test
	public void sensibleToString() {
		SimplePriorityBlockingQueue<Long> test = new SimplePriorityBlockingQueue<>(100, Long::compare);

		assertTrue(test.offer(4321L));
		assertTrue(test.offer(1234L));

		String s = test.toString();
		assertEquals("[1234, 4321]", s);
	}
}
