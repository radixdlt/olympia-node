package com.radixdlt.client.application.translate.tokenclasses;

import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.core.ledger.TransitionedParticle;
import java.math.BigDecimal;
import java.util.Collections;

import com.radixdlt.client.atommodel.tokens.MintedTokensParticle;
import org.junit.Test;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokenclasses.TokenState.TokenSupplyType;
import com.radixdlt.client.application.translate.tokens.TokenDefinitionReference;
import com.radixdlt.client.atommodel.tokens.TokenPermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenDefinitionsReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		TokenDefinitionReference tokenRef = mock(TokenDefinitionReference.class);
		when(tokenDefinitionParticle.getTokenDefinitionReference()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(MintedTokensParticle.class,
			TokenPermission.SAME_ATOM_ONLY));

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), TransitionedParticle.n2u(tokenDefinitionParticle));
		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO,
				TokenDefinitionReference.subunitsToUnits(1), TokenSupplyType.FIXED)
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		TokenDefinitionReference tokenRef = mock(TokenDefinitionReference.class);
		when(tokenDefinitionParticle.getTokenDefinitionReference()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(MintedTokensParticle.class,
			TokenPermission.TOKEN_OWNER_ONLY));

		MintedTokensParticle minted = mock(MintedTokensParticle.class);
		when(minted.getAmount()).thenReturn(hundred);
		when(minted.getTokenDefinitionReference()).thenReturn(tokenRef);

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state1 = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), TransitionedParticle.n2u(tokenDefinitionParticle));
		TokenDefinitionsState state2 = tokenDefinitionsReducer.reduce(state1, TransitionedParticle.n2u(minted));
		assertThat(state2.getState().get(tokenRef)).isEqualTo(
			new TokenState(
				"Name",
				"ISO",
				"Desc",
				TokenDefinitionReference.subunitsToUnits(hundred),
				TokenDefinitionReference.subunitsToUnits(1),
				TokenSupplyType.MUTABLE
			)
		);
	}
}