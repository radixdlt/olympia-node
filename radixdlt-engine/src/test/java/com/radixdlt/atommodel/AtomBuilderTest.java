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

import com.radixdlt.atom.Atom;
import com.radixdlt.atom.AtomBuilder;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.atom.ParticleGroup;
import com.radixdlt.atom.SpunParticle;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class AtomBuilderTest {

	@Test
	public void testCopyExcludingGroups() {
		Particle p = mock(Particle.class);
		ParticleGroup group1 = ParticleGroup.of(SpunParticle.up(p));
		ParticleGroup group2 = ParticleGroup.of(ImmutableList.of());
		AtomBuilder builder = Atom.newBuilder()
			.addParticleGroup(group1)
			.addParticleGroup(group2);

		assertEquals(2L, builder.particleGroups().count());

		AtomBuilder filteredAtom = builder.copyExcludingGroups(ParticleGroup::isEmpty);
		assertEquals(1L, filteredAtom.particleGroups().count());
		ParticleGroup testGroup = filteredAtom.particleGroups().findFirst().get();
		assertEquals(1, testGroup.getParticles().size());
		assertEquals(SpunParticle.up(p), testGroup.getSpunParticle(0));
	}
}
