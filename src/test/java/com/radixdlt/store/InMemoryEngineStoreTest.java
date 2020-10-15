package com.radixdlt.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.crypto.HashUtils;
import com.radixdlt.engine.BaseAtom;
import com.radixdlt.engine.RadixEngineAtom;
import org.junit.Before;
import org.junit.Test;

public class InMemoryEngineStoreTest {
	private InMemoryEngineStore<RadixEngineAtom> store;

	@Before
	public void setup() {
		this.store = new InMemoryEngineStore<>();
	}

	@Test
	public void when_store__then_can_retrieve_spin_of_particle() {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		RadixEngineAtom atom = new BaseAtom(cmInstruction, HashUtils.zero256());
		Particle particle = mock(Particle.class);
		when(cmInstruction.getMicroInstructions())
			.thenReturn(ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(particle, Spin.NEUTRAL)
			));

		this.store.storeAtom(atom);
		assertThat(this.store.getSpin(particle)).isEqualTo(Spin.UP);
	}

	@Test
	public void when_empty__then_can_retrieve_state() {
		Object state = mock(Object.class);
		Object nextState = this.store.compute(Particle.class, state, (o, p) -> mock(Object.class), (o, p) -> mock(Object.class));
		assertThat(state).isEqualTo(nextState);
	}

	@Test
	public void when_store__then_can_retrieve_state() {
		CMInstruction cmInstruction = mock(CMInstruction.class);
		RadixEngineAtom atom = new BaseAtom(cmInstruction, HashUtils.zero256());
		Particle particle = mock(Particle.class);
		when(cmInstruction.getMicroInstructions())
			.thenReturn(ImmutableList.of(
				CMMicroInstruction.checkSpinAndPush(particle, Spin.NEUTRAL)
			));

		this.store.storeAtom(atom);
		Object state = mock(Object.class);
		Object expectedState = mock(Object.class);
		Object nextState = this.store.compute(Particle.class, state, (o, p) -> expectedState, (o, p) -> mock(Object.class));
		assertThat(expectedState).isEqualTo(nextState);
	}

}