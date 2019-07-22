package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.engine.ValidationResult.ValidationResultAcceptor;
import java.util.Collections;
import org.junit.Test;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.CMStores;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.SerializerId2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
			.stateTransformer(state -> CMStores.virtualizeDefault(state, p -> true, Spin.UP))
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));

		CMAtom atom = mock(CMAtom.class);
		when(atom.getParticles()).thenReturn(ImmutableList.of(
			new CMParticle(p, DataPointer.ofParticle(0, 0), Spin.NEUTRAL, 1)
		));
		when(atom.getAtom()).thenReturn(mock(ImmutableAtom.class));

		ValidationResultAcceptor acceptor = mock(ValidationResultAcceptor.class);
		machine.validate(atom, true).accept(acceptor);
		verify(acceptor, times(1))
			.onError(argThat(s -> s.contains(
				new CMError(DataPointer.ofParticle(0, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT))
			));

	}

	@Test
	public void when_validating_an_atom_with_an_atom_kernel_compute__the_computation_is_returned() {
		ConstraintMachine machine = new ConstraintMachine.Builder()
			.stateTransformer(s -> CMStores.virtualizeDefault(s, p -> true, Spin.NEUTRAL))
			.addCompute("test", a -> "hello")
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));

		CMAtom atom = mock(CMAtom.class);
		when(atom.getAtom()).thenReturn(mock(ImmutableAtom.class));
		when(atom.getParticles()).thenReturn(ImmutableList.of(
			new CMParticle(p, DataPointer.ofParticle(0, 0), Spin.NEUTRAL, 1)
		));

		ValidationResultAcceptor acceptor = mock(ValidationResultAcceptor.class);
		machine.validate(atom, true).accept(acceptor);
		verify(acceptor, times(1))
			.onSuccess(argThat(m -> m.get("test").equals("hello")));
	}
}