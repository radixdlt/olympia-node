/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the “Software”),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.radixdlt.client.application.translate.tokens;

import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle.TokenTransition;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.client.atommodel.tokens.UnallocatedTokensParticle;
import com.radixdlt.identifiers.RRI;
import java.util.Collections;

import org.junit.Test;
import com.radixdlt.utils.UInt256;

import com.radixdlt.client.application.translate.tokens.TokenState.TokenSupplyType;
import com.radixdlt.atommodel.tokens.FixedSupplyTokenDefinitionParticle;
import com.radixdlt.atommodel.tokens.TokenPermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TokenDefinitionsReducerTest {
	@Test
	public void testTokenWithNoMint() {
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		RRI tokenRef = mock(RRI.class);
		when(tokenRef.getName()).thenReturn("ISO");
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
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
		when(tokenRef.getName()).thenReturn("ISO");
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
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
		when(tokenRef.getName()).thenReturn("ISO");
		when(tokenDefinitionParticle.getRRI()).thenReturn(tokenRef);
		when(tokenDefinitionParticle.getName()).thenReturn("Name");
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