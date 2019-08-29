package com.radixdlt.atomos;

import com.google.common.reflect.TypeToken;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.TransitionToken;
import com.radixdlt.constraintmachine.VoidUsedData;
import java.util.function.Function;
import org.junit.Test;

import com.radixdlt.constraintmachine.Particle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

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
		TransitionProcedure<TestParticle0, VoidUsedData, TestParticle0, VoidUsedData> procedure = mock(TransitionProcedure.class);
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle0.class, (TestParticle0 p) -> mock(RadixAddress.class), t -> Result.success());
			syscalls.createTransition(
				new TransitionToken<>(
					TestParticle0.class,
					TypeToken.of(VoidUsedData.class),
					TestParticle0.class,
					TypeToken.of(VoidUsedData.class)
				),
				procedure
			);
		});
		TransitionProcedure<TestParticle1, VoidUsedData, TestParticle0, VoidUsedData> procedure0 = mock(TransitionProcedure.class);
		assertThatThrownBy(() ->
			os.load(syscalls -> {
				syscalls.registerParticle(TestParticle1.class, (TestParticle1 p) -> mock(RadixAddress.class), t -> Result.success());
				syscalls.createTransition(
					new TransitionToken<>(
						TestParticle1.class,
						TypeToken.of(VoidUsedData.class),
						TestParticle0.class,
						TypeToken.of(VoidUsedData.class)
					),
					procedure0
				);
			})
		).isInstanceOf(IllegalStateException.class);
	}


	@Test
	public void when_a_particle_which_is_not_registered_via_os_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS();
		Function<Particle, Result> staticCheck = os.buildParticleStaticCheck();
		TestParticle testParticle = new TestParticle();
		assertThat(staticCheck.apply(testParticle).getErrorMessage())
			.contains("Unknown particle type");
	}

	@Test
	public void when_a_particle_with_a_bad_address_is_validated__it_should_cause_errors() {
		CMAtomOS os = new CMAtomOS(addr -> Result.error("Bad address"));
		os.load(syscalls -> {
			syscalls.registerParticle(TestParticle.class, p -> mock(RadixAddress.class), p -> Result.success());
		});
		Function<Particle, Result> staticCheck = os.buildParticleStaticCheck();
		TestParticle testParticle = new TestParticle();
		assertThat(staticCheck.apply(testParticle).getErrorMessage())
			.contains("Bad address");
	}
}