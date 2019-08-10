package com.radixdlt.atommodel.procedures;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atommodel.procedures.NonRRIResourceCreation;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.SysCalls.WitnessValidator;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ConstraintProcedure.ProcedureResult;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.atoms.Particle;

public class NonRRIResourceCreationTest {

	@SerializerId2("custom.payload.particle")
	private static class CustomPayloadParticle extends Particle {
		@Override
		public String toString() {
			return "payload";
		}
	}

	@Test
	public void when_a_payload_constraint_procedure_validates_an_up_particle__then_output_should_succeed() {
		WitnessValidator<CustomPayloadParticle> witnessValidator = mock(WitnessValidator.class);
		when(witnessValidator.validate(any(), any())).thenReturn(Result.success());
		ConstraintProcedure procedure = new NonRRIResourceCreation<>(CustomPayloadParticle.class, witnessValidator);

		ProcedureResult result = procedure.execute(null, null, mock(CustomPayloadParticle.class), new AtomicReference<>());

		assertThat(result).isEqualTo(ProcedureResult.POP_OUTPUT);
	}

	@Test
	public void when_a_payload_constraint_procedure_validates_a_downed_particle__then_an_error_should_be_returned() {
		WitnessValidator<CustomPayloadParticle> witnessValidator = mock(WitnessValidator.class);
		when(witnessValidator.validate(any(), any())).thenReturn(Result.success());

		ConstraintProcedure procedure = new NonRRIResourceCreation<>(CustomPayloadParticle.class, witnessValidator);
		ProcedureResult result = procedure.execute(
			new CustomPayloadParticle(),
			new AtomicReference<>(),
			mock(Particle.class),
			new AtomicReference<>()
		);
		assertThat(result).isEqualTo(ProcedureResult.POP_OUTPUT);
	}
}