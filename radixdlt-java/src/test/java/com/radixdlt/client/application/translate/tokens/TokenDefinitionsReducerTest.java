package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.client.core.atoms.particles.RRI;
import java.util.Collections;

import org.junit.Test;
import org.radix.common.ID.EUID;
import org.radix.utils.UInt256;

import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.client.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.client.atommodel.tokens.TokenPermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenDefinitionsReducerTest {
	@Test
	public void testTokenWithNoMint() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RRI tokenRef = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getIconUrl()).thenReturn("http://foo");
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getTokenPermissions()).thenReturn(Collections.singletonMap(TokenTransition.MINT,
			TokenPermission.ALL));

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), tokenDefinitionParticle);

		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", "http://foo",
				null,
				TokenUnitConversions.subunitsToUnits(1), TokenSupplyType.MUTABLE)
		);
	}

	@Test
	public void testFixedTokenWithNoMint() {
		FixedSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(FixedSupplyTokenDefinitionParticle.class);
		RRI tokenRef = mock(RRI.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
		when(tokenDefinitionParticle.getSymbol()).thenReturn("ISO");
		when(tokenDefinitionParticle.getDescription()).thenReturn("Desc");
		when(tokenDefinitionParticle.getSupply()).thenReturn(UInt256.ONE);
		when(tokenDefinitionParticle.getGranularity()).thenReturn(UInt256.ONE);

		TokenDefinitionsReducer tokenDefinitionsReducer = new TokenDefinitionsReducer();
		TokenDefinitionsState state = tokenDefinitionsReducer.reduce(
			TokenDefinitionsState.init(), tokenDefinitionParticle);

		assertThat(state.getState().get(tokenRef)).isEqualTo(
			new TokenState("Name", "ISO", "Desc", "http://foo",
				TokenUnitConversions.subunitsToUnits(1),
				TokenUnitConversions.subunitsToUnits(1), TokenSupplyType.FIXED)
		);
	}

	@Test
	public void testTokenWithMint() {
		final UInt256 hundred = UInt256.TEN.pow(2);
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
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
		TokenDefinitionsState state1 = tokenDefinitionsReducer.reduce(TokenDefinitionsState.init(), tokenDefinitionParticle);
		TokenDefinitionsState state2 = tokenDefinitionsReducer.reduce(state1, unallocatedTokensParticle);
		TokenDefinitionsState state3 = tokenDefinitionsReducer.reduce(state2, minted);
		assertThat(state3.getState().get(tokenRef).getTotalSupply()).isEqualTo(TokenUnitConversions.subunitsToUnits(hundred));
		assertThat(state3.getState().get(tokenRef).getName()).isEqualTo("Name");
		assertThat(state3.getState().get(tokenRef).getIso()).isEqualTo("ISO");
		assertThat(state3.getState().get(tokenRef).getTokenSupplyType()).isEqualTo(TokenSupplyType.MUTABLE);
	}
}