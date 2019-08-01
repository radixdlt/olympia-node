package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.ImmutableAtom;
import java.util.Collections;
import org.junit.Test;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.SerializerId2;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ConstraintMachineTest {
	@SerializerId2("test.indexed_particle_2")
	private class IndexedParticle extends Particle {
		@Override
		public String toString() {
			return String.format("%s", getClass().getSimpleName());
		}
	}

	@Test
	public void when_validating_an_atom_with_particle_which_conflicts_with_virtual_state__an_internal_spin_conflict_is_returned() {
		ConstraintMachine machine = new ConstraintMachine.Builder()
			.virtualStore(state -> CMStores.virtualizeDefault(state, p -> true, Spin.UP))
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));

		CMAtom atom = mock(CMAtom.class);
		when(atom.getParticles()).thenReturn(ImmutableList.of(
			new CMParticle(p, DataPointer.ofParticle(0, 0), Spin.NEUTRAL, 1)
		));
		when(atom.getAtom()).thenReturn(mock(ImmutableAtom.class));

		assertThat(machine.validate(atom, true))
			.contains(new CMError(DataPointer.ofParticle(0, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT));
	}

}