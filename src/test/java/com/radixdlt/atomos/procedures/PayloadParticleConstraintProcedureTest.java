package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;

import org.junit.Test;

import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.serialization.SerializerId2;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;

public class PayloadParticleConstraintProcedureTest {

	@SerializerId2("custom.payload.particle")
	private static class CustomPayloadParticle extends Particle {
		@Override
		public String toString() {
			return "payload";
		}
	}

	@Test
	public void when_a_payload_constraint_procedure_validates_an_up_particle__then_an_error_should_be_returned() {
		PayloadParticleConstraintProcedure procedure = new PayloadParticleConstraintProcedure.Builder()
			.add(CustomPayloadParticle.class)
			.build();

		Stream<ProcedureError> errors = procedure.validate(ParticleGroup.of(SpunParticle.up(new CustomPayloadParticle())), mock(AtomMetadata.class));

		assertThat(errors).isEmpty();
	}

	@Test
	public void when_a_payload_constraint_procedure_validates_a_downed_particle__then_an_error_should_be_returned() {
		PayloadParticleConstraintProcedure procedure = new PayloadParticleConstraintProcedure.Builder()
			.add(CustomPayloadParticle.class)
			.build();

		Stream<ProcedureError> errors = procedure.validate(ParticleGroup.of(SpunParticle.down(new CustomPayloadParticle())), mock(AtomMetadata.class));

		assertThat(errors).anyMatch(e -> e.getErrMsg().contains("cannot be DOWN"));
	}
}