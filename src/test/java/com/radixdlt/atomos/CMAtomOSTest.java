package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import java.util.Optional;
import org.junit.Test;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.universe.Universe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CMAtomOSTest {
	private static class TestParticle extends Particle {
		@Override
		public String toString() {
			return "Test";
		}

		@Override
		public boolean equals(Object o) {
			return this == o;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}

	private abstract static class TestParticle0 extends Particle {
	}

	private abstract static class TestParticle1 extends Particle {
	}

	@Test
	public void when_a_particle_which_is_not_registered_via_os_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		ConstraintMachine machine = os.buildMachine().getFirst();
		CMAtom atom = mock(CMAtom.class);
		when(atom.getAtom()).thenReturn(mock(ImmutableAtom.class));
		TestParticle testParticle = new TestParticle();
		when(atom.getParticles()).thenReturn(ImmutableList.of(
			new CMParticle(testParticle, DataPointer.ofParticle(0, 0), Spin.NEUTRAL, 1)
		));
		assertThat(machine.validate(atom))
			.isPresent()
			.matches(e -> e.get().getErrorCode() == CMErrorCode.UNKNOWN_PARTICLE);
	}

	@Test
	public void when_adding_procedure_on_particle_registered_in_another_scrypt__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		TransitionProcedure<TestParticle0, TestParticle0> procedure = mock(TransitionProcedure.class);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class), t -> Result.success());
			syscalls.createTransition(
				TestParticle0.class,
				TestParticle0.class,
				procedure,
				(res, in, out, meta) -> Optional.empty()
			);
		});
		TransitionProcedure<TestParticle1, TestParticle0> procedure0 = mock(TransitionProcedure.class);
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.registerParticle(TestParticle1.class, (TestParticle1 p) -> mock(RadixAddress.class), t -> Result.success());
				syscalls.createTransition(
					TestParticle1.class,
					TestParticle0.class,
					procedure0,
					(res, in, out, meta) -> Optional.empty()
				);
			})
		).isInstanceOf(IllegalStateException.class);
	}
}