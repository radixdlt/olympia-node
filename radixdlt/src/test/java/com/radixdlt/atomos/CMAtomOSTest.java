package com.radixdlt.atomos;

import org.junit.Test;

import com.radixdlt.atoms.Atom;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.universe.Universe;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

public class CMAtomOSTest {
	private static class TestParticle extends Particle {
		@Override
		public String toString() {
			return "Test";
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}

	@Test
	public void when_a_particle_which_is_not_registered_via_os_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		ConstraintMachine machine = os.buildMachine();
		Atom atom = new Atom(0);
		atom.addParticleGroupWith(new TestParticle(), Spin.UP);
		assertThat(machine.validate(atom, true).getErrors()).anyMatch(e -> e.getErrorCode() == CMErrorCode.UNKNOWN_PARTICLE);
	}

	@Test
	public void when_a_compute_is_registered_with_duplicate_keys_and_the_machine_is_built__an_illegal_state_exception_should_occur() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		assertThatThrownBy(() -> os.loadKernelConstraintScrypt(kernel -> {
			kernel.onAtom().compute("duplicate", atom -> "hello");
			kernel.onAtom().compute("duplicate", atom -> "hello");
		})).isInstanceOf(IllegalStateException.class);
	}
}