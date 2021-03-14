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

package com.radixdlt.atommodel;

import com.google.common.hash.HashCode;
import com.radixdlt.atom.Atom;
import com.radixdlt.atom.AtomAlreadySignedException;
import com.radixdlt.crypto.ECKeyPair;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.crypto.Hasher;
import com.radixdlt.crypto.exception.PublicKeyException;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AtomTest {

	@Test
	public void testCopyExcludingGroups() {
		Particle p = mock(Particle.class);
		ParticleGroup group1 = ParticleGroup.of(SpunParticle.up(p));
		ParticleGroup group2 = ParticleGroup.of(ImmutableList.of());
		Atom atom = new Atom(ImmutableList.of(group1, group2), ImmutableMap.of());

		assertEquals(2L, atom.particleGroups().count());

		Atom filteredAtom = atom.copyExcludingGroups(ParticleGroup::isEmpty);
		assertEquals(1L, filteredAtom.particleGroups().count());
		ParticleGroup testGroup = filteredAtom.particleGroups().findFirst().get();
		assertEquals(1, testGroup.getParticles().size());
		assertEquals(SpunParticle.up(p), testGroup.getSpunParticle(0));
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(Atom.class)
				.withPrefabValues(HashCode.class, HashUtils.random256(), HashUtils.random256())
				.verify();
	}

	@Test
	public void testAtomSignAndVerify() throws AtomAlreadySignedException, PublicKeyException {
		final Atom atom = new Atom();

		final Hasher hasher = mock(Hasher.class);
		when(hasher.hash(atom)).thenReturn(HashCode.fromLong(1234));

		final ECKeyPair key = ECKeyPair.generateNew();

		atom.sign(key, hasher);
		assertTrue(atom.verify(key.getPublicKey(), hasher));
	}
}
