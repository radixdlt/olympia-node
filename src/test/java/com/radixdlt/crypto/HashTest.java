/*
 *
 *  * (C) Copyright 2020 Radix DLT Ltd
 *  *
 *  * Radix DLT Ltd licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except in
 *  * compliance with the License.  You may obtain a copy of the
 *  * License at
 *  *
 *  *  http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  * either express or implied.  See the License for the specific
 *  * language governing permissions and limitations under the License.
 *
 */

package com.radixdlt.crypto;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atommodel.Atom;
import com.radixdlt.atomos.RRIParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HashTest {
	@Test
	public void hash_test_atom() {
		Atom atom = new Atom(123456789L);
		Hash hash = atom.getHash();
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("e7c0184f951334f75d494996fcbbff7437185752d6e1d105d8eda703bc8fce13", hashHex);
	}

	@Test
	public void hash_test_particle() {
		RadixAddress address = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RRI rri = RRI.of(address, "FOOBAR");
		RRIParticle particle = new RRIParticle(rri);
		Hash hash = particle.getHash();
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("a29e3505d9736f4de2a576b2fee1b6a449e56f6b3cbaa86b8388e39a1557c53a", hashHex);
	}

	private void assertIsNotRawDSON(Hash hash) {
		String hashHex = hash.toString();
		// CBOR/DSON encoding of an object starts with "bf" and ends with "ff", so we are here making
		// sure that Hash of the object is not just the DSON output, but rather a 256 bit hash digest of it.
		// the probability of 'accidentally' getting getting these prefixes and suffixes anyway is minimal (1/2^16)
		// for any DSON bytes as argument.
		assertFalse(hashHex.startsWith("bf") && hashHex.endsWith("ff"));
	}
}
