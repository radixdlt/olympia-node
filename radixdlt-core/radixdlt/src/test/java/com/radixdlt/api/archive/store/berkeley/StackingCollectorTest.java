/*
 * (C) Copyright 2021 Radix DLT Ltd
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

package com.radixdlt.client.store.berkeley;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class StackingCollectorTest {

	@Test
	public void valuesCanBePushedAndThenProcessed() {
		var collector = StackingCollector.<String>create();

		collector.push("1");

		var list1 = new ArrayList<String>();
		collector.consumeCollected(list1::add);

		assertEquals(1, list1.size());
		assertEquals("1", list1.get(0));

		collector.push("2");

		var list2 = new ArrayList<String>();
		collector.consumeCollected(list2::add);

		assertEquals(1, list2.size());
		assertEquals("2", list2.get(0));
	}
}