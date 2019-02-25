package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.core.ledger.TransitionedParticle;
import java.math.BigDecimal;
import java.util.Collections;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenTypeReference;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenTypesReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenTypeReference tokenRef = mock(TokenTypeReference.class);
		when(tokenParticle.getTokenTypeReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");
		when(tokenParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(MintedTokensParticle.class,
			TokenPermission.SAME_ATOM_ONLY));

		TokenTypesReducer tokenTypesReducer = new TokenTypesReducer();
		TokenTypesState state = tokenTypesReducer.reduce(TokenTypesState.init(), TransitionedParticle.n2u(tokenParticle));
		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO,
				TokenTypeReference.subunitsToUnits(1), TokenSupplyType.FIXED)
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenTypeReference tokenRef = mock(TokenTypeReference.class);
		when(tokenParticle.getTokenTypeReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");
		when(tokenParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(MintedTokensParticle.class,
			TokenPermission.TOKEN_OWNER_ONLY));

		MintedTokensParticle minted = mock(MintedTokensParticle.class);
		when(minted.getAmount()).thenReturn(hundred);
		when(minted.getTokenTypeReference()).thenReturn(tokenRef);

		TokenTypesReducer tokenTypesReducer = new TokenTypesReducer();
		TokenTypesState state1 = tokenTypesReducer.reduce(TokenTypesState.init(), TransitionedParticle.n2u(tokenParticle));
		TokenTypesState state2 = tokenTypesReducer.reduce(state1, TransitionedParticle.n2u(minted));
		assertThat(state2.getState().get(tokenRef)).isEqualTo(
			new TokenState(
				"Name",
				"ISO",
				"Desc",
				TokenTypeReference.subunitsToUnits(hundred),
				TokenTypeReference.subunitsToUnits(1),
				TokenSupplyType.MUTABLE
			)
		);
	}
}