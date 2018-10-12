package com.radixdlt.client.application.translate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.client.core.atoms.TokenRef;
import com.radixdlt.client.core.atoms.particles.Minted;
import com.radixdlt.client.core.atoms.particles.TokenParticle;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class TokenReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenRef tokenRef = mock(TokenRef.class);
		when(tokenParticle.getTokenRef()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getIso()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenRef, TokenState> state = tokenReducer.reduce(Collections.emptyMap(), tokenParticle);
		assertThat(state.get(tokenRef))
			.isEqualToComparingFieldByField(new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO));
	}

	@Test
	public void testTokenWithMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenRef tokenRef = mock(TokenRef.class);
		when(tokenParticle.getTokenRef()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getIso()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");

		Minted minted = mock(Minted.class);
		when(minted.getAmount()).thenReturn(100L);
		when(minted.getTokenRef()).thenReturn(tokenRef);

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenRef, TokenState> state1 = tokenReducer.reduce(Collections.emptyMap(), tokenParticle);
		Map<TokenRef, TokenState> state2 = tokenReducer.reduce(state1, minted);
		assertThat(state2.get(tokenRef))
			.isEqualToComparingFieldByField(
				new TokenState("Name", "ISO", "Desc", TokenRef.subUnitsToDecimal(100L))
			);
	}
}