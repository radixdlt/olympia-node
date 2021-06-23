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
package com.radixdlt.api.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MovingAverageTest {
	@Test
	public void emptyAverageIsZero() {
		assertEquals(0, MovingAverage.create(2).asLong());
	}

	@Test
	public void addingValuesUpdatesAverage() {
		var avg = MovingAverage.create(5);

		assertEquals(0, avg.asInteger());
		assertEquals(10, avg.update(10).asInteger());
		assertEquals(10, avg.update(10).asInteger());
		assertEquals(10, avg.update(10).asInteger());
		assertEquals(10, avg.update(10).asInteger());
		assertEquals(10, avg.update(10).asInteger());
		assertEquals(12, avg.update(20).asInteger());
		assertEquals(13, avg.update(20).asInteger());
		assertEquals(14, avg.update(20).asInteger());
		assertEquals(17, avg.update(30).asInteger());
		assertEquals(20, avg.update(30).asInteger());
		assertEquals(20, avg.update(20).asInteger());
		assertEquals(18, avg.update(10).asInteger());
		assertEquals(14, avg.update(0).asInteger());
		assertEquals(11, avg.update(0).asInteger());
		assertEquals(9, avg.update(0).asInteger());
		assertEquals(7, avg.update(0).asInteger());
		assertEquals(5, avg.update(0).asInteger());
		assertEquals(4, avg.update(0).asInteger());
		assertEquals(3, avg.update(0).asInteger());
		assertEquals(3, avg.update(0).asInteger());
		assertEquals(2, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(0, avg.update(0).asInteger());
	}

	@Test
	public void addingValuesUpdatesAverage1() {
		var avg = MovingAverage.create(10);

		assertEquals(0, avg.asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(315, avg.update(315).asInteger());
		assertEquals(283, avg.update(0).asInteger());
		assertEquals(255, avg.update(0).asInteger());
		assertEquals(229, avg.update(0).asInteger());
		assertEquals(206, avg.update(0).asInteger());
		assertEquals(186, avg.update(0).asInteger());
		assertEquals(167, avg.update(0).asInteger());
		assertEquals(150, avg.update(0).asInteger());
		assertEquals(135, avg.update(0).asInteger());
		assertEquals(122, avg.update(0).asInteger());
		assertEquals(109, avg.update(0).asInteger());
		assertEquals(98, avg.update(0).asInteger());
		assertEquals(88, avg.update(0).asInteger());
		assertEquals(80, avg.update(0).asInteger());
		assertEquals(72, avg.update(0).asInteger());
		assertEquals(64, avg.update(0).asInteger());
		assertEquals(58, avg.update(0).asInteger());
		assertEquals(52, avg.update(0).asInteger());
		assertEquals(47, avg.update(0).asInteger());
		assertEquals(42, avg.update(0).asInteger());
		assertEquals(38, avg.update(0).asInteger());
		assertEquals(34, avg.update(0).asInteger());
		assertEquals(31, avg.update(0).asInteger());
		assertEquals(27, avg.update(0).asInteger());
		assertEquals(25, avg.update(0).asInteger());
		assertEquals(22, avg.update(0).asInteger());
		assertEquals(20, avg.update(0).asInteger());
		assertEquals(18, avg.update(0).asInteger());
		assertEquals(16, avg.update(0).asInteger());
		assertEquals(14, avg.update(0).asInteger());
		assertEquals(13, avg.update(0).asInteger());
		assertEquals(12, avg.update(0).asInteger());
		assertEquals(10, avg.update(0).asInteger());
		assertEquals(9, avg.update(0).asInteger());
		assertEquals(8, avg.update(0).asInteger());
		assertEquals(7, avg.update(0).asInteger());
		assertEquals(7, avg.update(0).asInteger());
		assertEquals(6, avg.update(0).asInteger());
		assertEquals(5, avg.update(0).asInteger());
		assertEquals(5, avg.update(0).asInteger());
		assertEquals(4, avg.update(0).asInteger());
		assertEquals(4, avg.update(0).asInteger());
		assertEquals(3, avg.update(0).asInteger());
		assertEquals(3, avg.update(0).asInteger());
		assertEquals(3, avg.update(0).asInteger());
		assertEquals(2, avg.update(0).asInteger());
		assertEquals(2, avg.update(0).asInteger());
		assertEquals(2, avg.update(0).asInteger());
		assertEquals(2, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(1, avg.update(0).asInteger());
		assertEquals(0, avg.update(0).asInteger());
	}
}
