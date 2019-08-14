package com.radixdlt.atomos;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure.CMAction;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import org.junit.Test;

public class RRIResourceCombinedPrimaryCreationTest {
	private static class Resource extends Particle {
	}

	@Test
	public void when_i_validate_an_input_output_with_remainder_from_previous_result__then_an_error_should_be_returned() {
		RRI rri = mock(RRI.class);
		RRIParticle rriParticle = mock(RRIParticle.class);
		when(rriParticle.getRri()).thenReturn(rri);
		RRIResourceCombinedPrimaryCreation<Resource> primaryCreation = new RRIResourceCombinedPrimaryCreation<>(r -> rri);
		ProcedureResult result = primaryCreation.execute(rriParticle, mock(Resource.class), ProcedureResult.popOutput(rriParticle));
		assertThat(result.getCmAction()).isEqualTo(CMAction.ERROR);
	}
}