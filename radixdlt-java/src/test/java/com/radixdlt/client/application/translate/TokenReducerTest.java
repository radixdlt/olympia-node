package com.radixdlt.client.application.translate;

import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.core.atoms.particles.SpunParticle;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenClassReference tokenRef = mock(TokenClassReference.class);
		when(tokenParticle.getTokenClassReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenClassReference, TokenState> state = tokenReducer.reduce(Collections.emptyMap(), SpunParticle.up(tokenParticle));
		assertThat(state.get(tokenRef))
				.isEqualToComparingFieldByField(new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO));
	}

	@Test
	public void testTokenWithMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenClassReference tokenRef = mock(TokenClassReference.class);
		when(tokenParticle.getTokenClassReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");

		OwnedTokensParticle minted = mock(OwnedTokensParticle.class);
		when(minted.getAmount()).thenReturn(100L);
		when(minted.getType()).thenReturn(FungibleQuark.FungibleType.MINTED);
		when(minted.getTokenClassReference()).thenReturn(tokenRef);

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenClassReference, TokenState> state1 = tokenReducer.reduce(Collections.emptyMap(), SpunParticle.up(tokenParticle));
		Map<TokenClassReference, TokenState> state2 = tokenReducer.reduce(state1, SpunParticle.up(minted));
		assertThat(state2.get(tokenRef))
				.isEqualToComparingFieldByField(
						new TokenState("Name", "ISO", "Desc", TokenClassReference.subUnitsToDecimal(100L))
				);
	}
}