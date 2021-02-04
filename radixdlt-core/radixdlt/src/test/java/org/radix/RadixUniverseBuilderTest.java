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

package org.radix;

import org.junit.Test;

import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.universe.Universe;
import com.radixdlt.universe.Universe.UniverseType;
import com.radixdlt.utils.Pair;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RadixUniverseBuilderTest {
	@Test
	public void testDevUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.development().build();

		assertTrue(p.getSecond().isDevelopment());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testTestUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.test().build();

		assertTrue(p.getSecond().isTest());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testProdUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.production().build();

		assertTrue(p.getSecond().isProduction());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testSpecifiedTypeUniverse() {
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.forType(UniverseType.PRODUCTION)
			.withNewKey()
			.build();

		assertTrue(p.getSecond().isProduction());
		assertEquals(1, p.getSecond().getGenesis().size());
	}

	@Test
	public void testSpecificKeyAndTimestampUniverse() {
		ECKeyPair newKey = ECKeyPair.generateNew();
		long timestamp = System.currentTimeMillis();
		Pair<ECKeyPair, Universe> p = RadixUniverseBuilder.test()
			.withKey(newKey)
			.withTimestamp(timestamp)
			.build();

		assertTrue(p.getSecond().isTest());
		assertEquals(1, p.getSecond().getGenesis().size());
		assertEquals(newKey.getPublicKey(), p.getSecond().getCreator());
		assertEquals(timestamp, p.getSecond().getTimestamp());
	}
}
