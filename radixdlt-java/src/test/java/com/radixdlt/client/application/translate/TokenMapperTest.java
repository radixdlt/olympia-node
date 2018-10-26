package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.CreateFixedSupplyTokenAction;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.core.atoms.particles.TransferParticle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import java.util.List;

import org.junit.Test;

public class TokenMapperTest {
	@Test
	public void testNormalConstruction() {
		CreateFixedSupplyTokenAction tokenCreation = mock(CreateFixedSupplyTokenAction.class);
		AccountReference accountReference = mock(AccountReference.class);
		when(tokenCreation.getAccountReference()).thenReturn(accountReference);
		when(tokenCreation.getIso()).thenReturn("ISO");
		when(tokenCreation.getFixedSupply()).thenReturn(1L);

		TokenMapper tokenMapper = new TokenMapper();
		List<SpunParticle> particles = tokenMapper.map(tokenCreation);
		assertThat(particles).anySatisfy(s -> assertThat(s.getParticle()).isInstanceOf(TokenParticle.class));
		assertThat(particles).anySatisfy(s -> assertThat(s.getParticle()).isInstanceOf(TransferParticle.class));
		assertThat(particles).hasSize(2);
	}
}