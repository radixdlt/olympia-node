package com.radixdlt.engine;

import com.google.common.collect.ImmutableList;
import com.radixdlt.common.Atom;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.middleware.ParticleGroup;
import com.radixdlt.middleware.SpunParticle;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import org.junit.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RadixEngineTest {

	@SerializerId2("test.indexed_particle_2")
	private class IndexedParticle extends Particle {
		@Override
		public String toString() {
			return String.format("%s", getClass().getSimpleName());
		}
	}

	@Test
	public void when_validating_an_atom_with_particle_which_conflicts_with_virtual_state__an_internal_spin_conflict_is_returned() {
		ConstraintMachine constraintMachine = mock(ConstraintMachine.class);
		when(constraintMachine.validate(any())).thenReturn(Optional.empty());
		EngineStore engineStore = mock(EngineStore.class);
		when(engineStore.supports(any())).thenReturn(true);
		RadixEngine engine = new RadixEngine(
			constraintMachine,
			state -> CMStores.virtualizeDefault(state, p -> true, Spin.DOWN),
			engineStore
		);

		engine.start();
		Atom atom = spy(new Atom());
		when(atom.getParticleGroups()).thenReturn(ImmutableList.of(ParticleGroup.of(SpunParticle.of(mock(IndexedParticle.class), Spin.UP))));
		AtomEventListener listener = mock(AtomEventListener.class);
		engine.store(atom, listener);
		verify(listener, timeout(200).times(1)).onVirtualStateConflict(any(), eq(DataPointer.ofParticle(0, 0)));
	}
}