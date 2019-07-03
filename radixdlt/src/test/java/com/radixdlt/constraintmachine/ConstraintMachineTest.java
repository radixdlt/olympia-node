package com.radixdlt.constraintmachine;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import java.util.Collections;
import org.junit.Test;
import org.radix.modules.Modules;
import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.store.StateStores;
import com.radixdlt.common.EUID;
import com.radixdlt.serialization.Serialization;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.serialization.core.ClasspathScanningSerializationPolicy;
import com.radixdlt.serialization.core.ClasspathScanningSerializerIds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
			.stateTransformer(state -> StateStores.virtualizeDefault(state, p -> true, Spin.UP))
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));
		Atom atom = new Atom();
		atom.addParticleGroupWith(p, Spin.UP);

		assertThat(machine.validate(atom, true).getErrors())
			.contains(new CMError(DataPointer.ofParticle(0, 0), CMErrorCode.INTERNAL_SPIN_CONFLICT));
	}

	@Test
	public void when_validating_an_atom_with_an_atom_kernel_compute__the_computation_is_returned() {
		ConstraintMachine machine = new ConstraintMachine.Builder()
			.stateTransformer(s -> StateStores.virtualizeDefault(s, p -> true, Spin.NEUTRAL))
			.addCompute("test", a -> "hello")
			.build();

		IndexedParticle p = mock(IndexedParticle.class);
		when(p.getDestinations()).thenReturn(Collections.singleton(EUID.ONE));

		Atom atom = new Atom();
		atom.addParticleGroupWith(p, Spin.UP);
		org.assertj.core.api.AssertionsForClassTypes.assertThat(
			machine.validate(atom, false).onSuccessElseThrow(e -> new IllegalStateException(e.toString()))
			.getComputedOrError("test", String.class))
			.isEqualTo("hello");
	}

	@BeforeClass
	public static void setupSerializer() {
		Serialization s = Serialization.create(ClasspathScanningSerializerIds.create(), ClasspathScanningSerializationPolicy.create());
		Modules.put(Serialization.class, s);
	}

	@AfterClass
	public static void cleanup() {
		Modules.remove(Serialization.class);
	}
}