package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.Spin;
import com.radixdlt.constraintmachine.CMError;
import com.radixdlt.constraintmachine.CMErrorCode;
import com.radixdlt.constraintmachine.CMInstruction;
import com.radixdlt.constraintmachine.CMMicroInstruction;
import com.radixdlt.constraintmachine.ConstraintMachine;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Optional;
import org.junit.Test;

import com.radixdlt.atoms.Particle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
	public void when_adding_procedure_on_particle_registered_in_another_scrypt__exception_is_thrown() {
		CMAtomOS os = new CMAtomOS();
		TransitionProcedure<TestParticle0, TestParticle0> procedure = mock(TransitionProcedure.class);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class), t -> Result.success());
			syscalls.createTransition(
				TestParticle0.class,
				TestParticle0.class,
				procedure,
				(res, in, out, meta) -> WitnessValidatorResult.success()
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
					(res, in, out, meta) -> WitnessValidatorResult.success()
				);
			})
		).isInstanceOf(IllegalStateException.class);
	}


	@Test
	public void when_a_particle_which_is_not_registered_via_os_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS();
		ConstraintMachine machine = os.buildMachine();
		CMInstruction instruction = mock(CMInstruction.class);
		TestParticle testParticle = new TestParticle();
		when(instruction.getMicroInstructions()).thenReturn(
			ImmutableList.of(CMMicroInstruction.checkSpin(testParticle, Spin.UP))
		);
		Optional<CMError> error = machine.validate(instruction);
		assertThat(error)
			.get()
			.satisfies(err -> {
				assertThat(err.getDataPointer()).isEqualTo(DataPointer.ofParticle(0, 0));
				assertThat(err.getErrorCode()).isEqualTo(CMErrorCode.INVALID_PARTICLE);
				assertThat(err.getErrorDescription()).contains("Unknown particle type");
			});
	}

	@Test
	public void when_a_particle_with_a_bad_address_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS(addr -> Result.error("Bad address"));
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle.class, p -> mock(RadixAddress.class), p -> Result.success());
		});
		ConstraintMachine machine = os.buildMachine();
		CMInstruction instruction = mock(CMInstruction.class);
		TestParticle testParticle = new TestParticle();
		when(instruction.getMicroInstructions()).thenReturn(
			ImmutableList.of(CMMicroInstruction.checkSpin(testParticle, Spin.UP))
		);
		Optional<CMError> error = machine.validate(instruction);
		assertThat(error)
			.get()
			.satisfies(err -> {
				assertThat(err.getDataPointer()).isEqualTo(DataPointer.ofParticle(0, 0));
				assertThat(err.getErrorCode()).isEqualTo(CMErrorCode.INVALID_PARTICLE);
				assertThat(err.getErrorDescription()).contains("Bad address");
			});
	}
}