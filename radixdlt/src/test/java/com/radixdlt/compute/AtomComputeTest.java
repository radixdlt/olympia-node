package com.radixdlt.compute;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.serialization.SerializerId2;
import java.util.Collections;
import org.junit.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class AtomComputeTest {
	@SerializerId2("test.indexed_particle_3")
	private class IndexedParticle extends Particle {
		@Override
		public String toString() {
			return String.format("%s", getClass().getSimpleName());
		}
	}

	@Test
	public void when_validating_an_atom_with_an_atom_kernel_compute__the_computation_is_returned() {
		AtomCompute atomCompute = new AtomCompute.Builder()
			.addCompute("test", a -> "hello")
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));

		CMAtom atom = mock(CMAtom.class);
		when(atom.getAtom()).thenReturn(mock(ImmutableAtom.class));
		when(atom.getParticles()).thenReturn(ImmutableList.of(
			new CMParticle(p, DataPointer.ofParticle(0, 0), Spin.NEUTRAL, 1)
		));

		assertThat(atomCompute.compute(atom))
			.containsEntry("test", "hello");
	}
}