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

package com.radixdlt.store.mvstore;

import org.junit.Test;

import static com.radixdlt.store.mvstore.KeyList.*;
import static org.junit.Assert.*;

public class KeyListTest {
	@Test
	public void emptyKeyListCanBeSerialized() {
		assertArrayEquals(new byte[]{0}, of().toBytes());
	}

	@Test
	public void nonEmptyKeyListCanBeSerialized() {
		assertArrayEquals(new byte[]{1, 0}, of(new byte[] {}).toBytes());
		assertArrayEquals(new byte[]{1, 1, 1}, of(new byte[] {1}).toBytes());
		assertArrayEquals(new byte[]{2, 1, 1, 1, 2}, of(new byte[] {1}, new byte[] {2}).toBytes());
	}

	@Test
	public void twoNonEmptyKeyListsCanBeMerged() {
		var merged = merge(
			of(new byte[] {3}).toBytes(),
			of(new byte[] {4}).toBytes()
		);
		assertArrayEquals(new byte[]{2, 1, 3, 1, 4}, merged);
	}

	@Test
	public void emptyKeyListCanBeMergedWithNonEmptyKeyList() {
		assertArrayEquals(new byte[]{1, 1, 2}, merge(null, of(new byte[] {2}).toBytes()));
		assertArrayEquals(new byte[]{1, 1, 3}, merge(of(new byte[] {3}).toBytes(), null));
	}
}