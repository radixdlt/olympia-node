package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.CreateFixedSupplyToken;
import com.radixdlt.client.core.atoms.AccountReference;
import com.radixdlt.client.core.atoms.particles.Consumable;
import com.radixdlt.client.core.atoms.particles.Particle;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import java.util.List;
import org.junit.Test;

public class TokenMapperTest {
	@Test
	public void testNormalConstruction() {
		CreateFixedSupplyToken tokenCreation = mock(CreateFixedSupplyToken.class);
		AccountReference accountReference = mock(AccountReference.class);
		when(tokenCreation.getAccountReference()).thenReturn(accountReference);
		when(tokenCreation.getIso()).thenReturn("ISO");

		TokenMapper tokenMapper = new TokenMapper();
		List<Particle> particles = tokenMapper.map(tokenCreation);
		assertThat(particles).hasAtLeastOneElementOfType(TokenParticle.class);
		assertThat(particles).hasAtLeastOneElementOfType(Consumable.class);
		assertThat(particles).hasSize(2);
	}
}