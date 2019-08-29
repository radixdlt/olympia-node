package com.radixdlt.engine;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.DataPointer;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.store.CMStores;
import com.radixdlt.store.EngineStore;
import java.util.Optional;
import org.junit.Test;

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
		EngineStore<RadixEngineAtom> engineStore = mock(EngineStore.class);
		when(engineStore.supports(any())).thenReturn(true);
		RadixEngine<RadixEngineAtom> engine = new RadixEngine<>(
			constraintMachine,
			state -> CMStores.virtualizeDefault(state, p -> true, Spin.UP),
			engineStore
		);

		engine.start();
		IndexedParticle p = mock(IndexedParticle.class);
		CMInstruction instruction = new CMInstruction(
			ImmutableList.of(CMMicroInstruction.checkSpin(p, Spin.NEUTRAL)),
			null,
			ImmutableMap.of()
		);
		RadixEngineAtom atom = () -> instruction;
		AtomEventListener<RadixEngineAtom> listener = mock(AtomEventListener.class);
		engine.store(atom, listener);
		verify(listener, timeout(200).times(1)).onVirtualStateConflict(any(), eq(DataPointer.ofParticle(0, 0)));
	}
}