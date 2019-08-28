package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.radixdlt.constraintmachine.ConstraintMachine.CMValidationState;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.constraintmachine.WitnessValidator.WitnessValidatorResult;
import java.util.Optional;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ConstraintMachineTest {


	@Test
	public void when_validating_a_2_input_1_output_particle_group_which_pops_1_input_first__validation_should_succeed() {
		TransitionProcedure<Particle, UsedData, Particle, UsedData> procedure = mock(TransitionProcedure.class);
		when(procedure.execute(any(), any(), any(), any()))
			.thenReturn(ProcedureResult.popInput(null, (p, w) -> WitnessValidatorResult.success()))
			.thenReturn(ProcedureResult.popInputOutput(
				(p, w) -> WitnessValidatorResult.success(),
				(p, w) -> WitnessValidatorResult.success()
			));

		ConstraintMachine machine = new ConstraintMachine.Builder()
			.setParticleProcedures(t -> procedure)
			.build();

		Particle particle0 = mock(Particle.class);
		Particle particle1 = mock(Particle.class);
		Particle particle2 = mock(Particle.class);

		CMValidationState validationState = new CMValidationState(null, null);
		validationState.checkSpin(particle0, Spin.UP);
		validationState.checkSpin(particle1, Spin.UP);
		validationState.checkSpin(particle2, Spin.NEUTRAL);

		Optional<CMError> errors = machine.validateMicroInstructions(validationState,
			ImmutableList.of(
				CMMicroInstruction.push(particle0),
				CMMicroInstruction.push(particle1),
				CMMicroInstruction.push(particle2),
				CMMicroInstruction.particleGroup()
			)
		);

		assertThat(errors).isEmpty();
	}
}