package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMAtom;
import com.radixdlt.constraintmachine.CMParticle;
import com.radixdlt.utils.UInt256;
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
		assertThat(machine.validate(atom, true))
			.anyMatch(e -> e.getErrorCode() == CMErrorCode.UNKNOWN_PARTICLE);
	}

	@Test
	public void when_running_constraint_scrypt_with_unknown_particle_identifier__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class));
				syscalls.on(TestParticle1.class);
			})
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_running_constraint_scrypt_with_known_particle_identifier__exception_is_not_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class));
			syscalls.on(TestParticle0.class);
		});
	}

	@Test
	public void when_adding_constraints_on_particle_registered_in_another_scrypt__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class));
		});
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.on(TestParticle0.class).require(k -> Result.success());
			})
		).isInstanceOf(IllegalStateException.class);
	}

	@Test
	public void when_adding_fungible_constraints_on_particle_registered_in_another_scrypt__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS(() -> mock(Universe.class), () -> 0);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class));
			syscalls.onFungible(TestParticle0.class, k -> UInt256.ONE);
		});
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.registerParticle(TestParticle1.class, (TestParticle1 p) -> mock(RadixAddress.class));
				syscalls.onFungible(TestParticle1.class, k -> UInt256.ONE)
					.transitionTo(TestParticle0.class, (a, b) -> true, (a, m) -> Result.success());
			})
		).isInstanceOf(IllegalStateException.class);
	}
}