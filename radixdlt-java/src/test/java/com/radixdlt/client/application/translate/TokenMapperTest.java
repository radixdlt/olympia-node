package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.application.actions.CreateTokenAction;
import com.radixdlt.client.application.actions.CreateTokenAction.TokenSupplyType;
import com.radixdlt.client.atommodel.accounts.RadixAddress;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.core.crypto.ECPublicKey;
import java.util.List;

import org.junit.Test;

public class TokenMapperTest {
	@Test
	public void testNormalConstruction() {
		CreateTokenAction tokenCreation = mock(CreateTokenAction.class);
		RadixAddress address = mock(RadixAddress.class);
		ECPublicKey key = mock(ECPublicKey.class);
		when(address.getPublicKey()).thenReturn(key);
		when(tokenCreation.getAddress()).thenReturn(address);
		when(tokenCreation.getIso()).thenReturn("ISO");
		when(tokenCreation.getInitialSupply()).thenReturn(1L);
		when(tokenCreation.getTokenSupplyType()).thenReturn(TokenSupplyType.MUTABLE);

		TokenMapper tokenMapper = new TokenMapper();
		List<SpunParticle> particles = tokenMapper.map(tokenCreation);
		assertThat(particles).anySatisfy(s -> assertThat(s.getParticle()).isInstanceOf(TokenParticle.class));
		assertThat(particles).anySatisfy(s -> assertThat(s.getParticle()).isInstanceOf(OwnedTokensParticle.class));
		assertThat(particles).hasSize(2);
	}
}