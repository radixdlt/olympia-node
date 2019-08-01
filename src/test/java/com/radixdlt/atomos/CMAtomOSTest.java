package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import org.junit.Test;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.universe.Universe;

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
		assertThat(machine.validate(atom, true))
			.anyMatch(e -> e.getErrorCode() == CMErrorCode.UNKNOWN_PARTICLE);
	}
}