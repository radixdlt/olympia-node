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

package com.radixdlt.utils;

import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class InstantsTest {
	@Test
	public void toBytesWorksCorrectly() {
		var instant = Instant.ofEpochSecond(1620640302L, 820166000);
		var expected = new byte[] {0, 0, 0, 0, 96, -103, 2, 46, 48, -30, -67, 112};

		assertArrayEquals(expected, Instants.toBytes(instant));
	}

	@Test
	public void fromBytesWorksCorrectly() {
		var expected = Instant.ofEpochSecond(1620640302L, 820166000);
		var bytes = new byte[] {0, 0, 0, 0, 96, -103, 2, 46, 48, -30, -67, 112};

		assertEquals(expected, Instants.fromBytes(bytes));
	}
}