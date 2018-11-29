package com.radixdlt.client.application.translate.tokenclasses;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.quarks.FungibleQuark;
import com.radixdlt.client.atommodel.quarks.FungibleQuark.FungibleType;
import com.radixdlt.client.atommodel.tokens.OwnedTokensParticle;
import com.radixdlt.client.atommodel.tokens.TokenClassReference;
import com.radixdlt.client.atommodel.tokens.TokenParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;
import com.radixdlt.client.core.atoms.particles.SpunParticle;

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
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(FungibleType.MINTED, TokenPermission.SAME_ATOM_ONLY));

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenClassReference, TokenState> state = tokenReducer.reduce(Collections.emptyMap(), SpunParticle.up(tokenParticle));
		assertThat(state.get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO, TokenSupplyType.FIXED)
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		TokenParticle tokenParticle = mock(TokenParticle.class);
		TokenClassReference tokenRef = mock(TokenClassReference.class);
		when(tokenParticle.getTokenClassReference()).thenReturn(tokenRef);
		when(tokenParticle.getName()).thenReturn("Name");
		when(tokenParticle.getSymbol()).thenReturn("ISO");
		when(tokenParticle.getDescription()).thenReturn("Desc");
		when(tokenParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(FungibleType.MINTED, TokenPermission.TOKEN_OWNER_ONLY));

		OwnedTokensParticle minted = mock(OwnedTokensParticle.class);
		when(minted.getAmount()).thenReturn(hundred);
		when(minted.getType()).thenReturn(FungibleQuark.FungibleType.MINTED);
		when(minted.getTokenClassReference()).thenReturn(tokenRef);

		TokenReducer tokenReducer = new TokenReducer();
		Map<TokenClassReference, TokenState> state1 = tokenReducer.reduce(Collections.emptyMap(), SpunParticle.up(tokenParticle));
		Map<TokenClassReference, TokenState> state2 = tokenReducer.reduce(state1, SpunParticle.up(minted));
		assertThat(state2.get(tokenRef)).isEqualTo(
			new TokenState(
				"Name",
				"ISO",
				"Desc",
				TokenClassReference.subunitsToUnits(hundred),
				TokenSupplyType.MUTABLE
			)
		);
	}
}