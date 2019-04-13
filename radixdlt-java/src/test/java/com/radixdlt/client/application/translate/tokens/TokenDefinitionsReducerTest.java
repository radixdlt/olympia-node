package com.radixdlt.client.application.translate.tokens;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.RRI;
import com.radixdlt.client.core.ledger.TransitionedParticle;
import java.math.BigDecimal;
import java.util.Collections;

import org.junit.Test;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.TokenPermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenDefinitionsReducerTest {
	@Test
	public void testTokenWithNoMint() {
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		RRI tokenRef = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(TokenTransition.MINT,
			TokenPermission.TOKEN_CREATION_ONLY));

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), TransitionedParticle.n2u(tokenDefinitionParticle));

		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", BigDecimal.ZERO,
				TokenUnitConversions.subunitsToUnits(1), TokenSupplyType.FIXED, ImmutableMap.of())
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		TokenDefinitionParticle tokenDefinitionParticle = mock(TokenDefinitionParticle.class);
		RRI tokenRef = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(TokenTransition.MINT,
			TokenPermission.TOKEN_OWNER_ONLY));

		TransferrableTokensParticle minted = mock(TransferrableTokensParticle.class);
		when(minted.getAmount()).thenReturn(hundred);
		when(minted.getTokenDefinitionReference()).thenReturn(tokenRef);

		UnallocatedTokensParticle unallocatedTokensParticle = mock(UnallocatedTokensParticle.class);
		when(unallocatedTokensParticle.getAmount()).thenReturn(UInt256.MAX_VALUE.subtract(hundred));
		when(unallocatedTokensParticle.getTokDefRef()).thenReturn(tokenRef);
		when(unallocatedTokensParticle.getHid()).thenReturn(EUID.ONE);

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state1 = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), TransitionedParticle.n2u(tokenDefinitionParticle));
		TokenDefinitionsState state2 = tokenDefinitionsReducer.reduce(state1, TransitionedParticle.n2u(minted));
		TokenDefinitionsState state3 = tokenDefinitionsReducer.reduce(state2, TransitionedParticle.n2u(unallocatedTokensParticle));
		assertThat(state3.getState().get(tokenRef).getTotalSupply()).isEqualTo(TokenUnitConversions.subunitsToUnits(hundred));
		assertThat(state3.getState().get(tokenRef).getName()).isEqualTo("Name");
		assertThat(state3.getState().get(tokenRef).getIso()).isEqualTo("ISO");
		assertThat(state3.getState().get(tokenRef).getTokenSupplyType()).isEqualTo(TokenSupplyType.MUTABLE);
	}
}