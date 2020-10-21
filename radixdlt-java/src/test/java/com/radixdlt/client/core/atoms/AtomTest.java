/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.core.atoms;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;
import com.radixdlt.client.atommodel.rri.RRIParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AtomTest {
	@Test
	public void when_an_atom_has_multiple_destinations_to_the_same_address__calling_addresses_should_return_one_address() {
		RadixAddress address = mock(RadixAddress.class);
		Particle particle0 = mock(Particle.class);
		when(particle0.getShardables()).thenReturn(Collections.singleton(address));
		Particle particle1 = mock(Particle.class);
		when(particle1.getShardables()).thenReturn(Collections.singleton(address));
		Atom atom = Atom.create(ParticleGroup.of(SpunParticle.up(particle0), SpunParticle.up(particle1)));
		assertThat(atom.addresses()).containsExactly(address);
	}

	@Test
	public void hash_test_atom() {
		Atom atom = Atom.create(ImmutableList.of());
		HashCode hash = atom.getHash();
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("311964d6688530d47baba551393b9a51a5e6a22504133597e3f7c2af5f83a2ce", hashHex);
		assertFalse(atom.getAid().isZero());
	}

	@Test
	public void hash_test_particle() {
		RadixAddress address = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RRI rri = RRI.of(address, "FOOBAR");
		RRIParticle particle = new RRIParticle(rri);
		HashCode hash = particle.getHash();
		assertIsNotRawDSON(hash);
		String hashHex = hash.toString();
		assertEquals("a29e3505d9736f4de2a576b2fee1b6a449e56f6b3cbaa86b8388e39a1557c53a", hashHex);
	}

	private void assertIsNotRawDSON(HashCode hash) {
		String hashHex = hash.toString();
		// CBOR/DSON encoding of an object starts with "bf" and ends with "ff", so we are here making
		// sure that Hash of the object is not just the DSON output, but rather a 256 bit hash digest of it.
		// the probability of 'accidentally' getting getting these prefixes and suffixes anyway is minimal (1/2^16)
		// for any DSON bytes as argument.
		assertFalse(hashHex.startsWith("bf") && hashHex.endsWith("ff"));
	}
}
