package com.radixdlt.atomos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.radixdlt.atomos.procedures.ParticleClassConstraintProcedure;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ProcedureError;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import nl.jqno.equalsverifier.Func;
import org.junit.Test;

public class ParticleClassConstraintProcedureTest {
	private abstract class CustomParticle extends Particle {
		private CustomParticle() {
			super();
		}
	}

	@Test
	public void when_receive_atom_with_one_particle_with_class_that_is_checked__check_does_occur_once() {
		Function<CustomParticle, Result> constraintCheck = mock(Function.class);
		when(constraintCheck.apply(any())).thenReturn(Result.success());

		ParticleClassConstraintProcedure<CustomParticle> constraintProcedure = new ParticleClassConstraintProcedure<>(
			CustomParticle.class,
			constraintCheck
		);

		CustomParticle p = mock(CustomParticle.class);

		Stream<ProcedureError> issues = constraintProcedure.validate(ParticleGroup.of(SpunParticle.up(p)), mock(AtomMetadata.class));
		issues.forEach(i -> { });

		verify(constraintCheck, times(1)).apply(eq(p));
	}


	@Test
	public void when_receive_atom_with_one_down_particle_with_class_that_is_checked__check_does_not_occur() {
		Function<CustomParticle, Result> constraintCheck = mock(Function.class);
		when(constraintCheck.apply(any())).thenReturn(Result.success());

		ParticleClassConstraintProcedure<CustomParticle> constraintProcedure = new ParticleClassConstraintProcedure<>(
			CustomParticle.class,
			constraintCheck
		);

		CustomParticle p1 = mock(CustomParticle.class);

		Stream<ProcedureError> issues = constraintProcedure.validate(ParticleGroup.of(SpunParticle.down(p1)), mock(AtomMetadata.class));
		issues.forEach(i -> { });

		verify(constraintCheck, times(0)).apply(any());
	}
}