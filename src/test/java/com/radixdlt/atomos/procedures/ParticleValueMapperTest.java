package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.procedures.ParticleValueMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import org.junit.Test;

public class ParticleValueMapperTest {
	private abstract static class Oxygen extends Particle {
	}

	private abstract static class Hydrogen extends Particle {
	}

	private abstract static class H2O extends Particle {
	}

	@Test
	public void testValid() {
		assertThatThrownBy(() -> ParticleValueMapper.from(null));

		FungibleTransition h2oTransition = mock(FungibleTransition.class);
		when(h2oTransition.getOutputParticleClass()).thenReturn(H2O.class);
		when(h2oTransition.getOutputParticleToAmountMapper()).thenReturn(x -> UInt256.TEN);

		FungibleTransition oxygenTransition = mock(FungibleTransition.class);
		when(oxygenTransition.getOutputParticleClass()).thenReturn(Oxygen.class);
		when(oxygenTransition.getOutputParticleToAmountMapper()).thenReturn(x -> UInt256.SEVEN);

		ParticleValueMapper valueMapper = ParticleValueMapper.from(Arrays.asList(
			h2oTransition,
			oxygenTransition
		));
		assertEquals(UInt256.TEN, valueMapper.amount(mock(H2O.class)));
		assertEquals(UInt256.SEVEN, valueMapper.amount(mock(Oxygen.class)));
	}

	@Test
	public void testUnexpected() {
		assertThatThrownBy(() -> ParticleValueMapper.from(null));

		FungibleTransition transition = mock(FungibleTransition.class);
		when(transition.getOutputParticleClass()).thenReturn(H2O.class);
		when(transition.getOutputParticleToAmountMapper()).thenReturn(x -> UInt256.TEN);
		when(transition.getAllInputs()).thenReturn(ImmutableSet.of());

		ParticleValueMapper valueMapper = ParticleValueMapper.from(Arrays.asList(transition));
		assertThatThrownBy(() -> valueMapper.amount(mock(Oxygen.class)));
	}
}