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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.atommodel.tokens.MutableSupplyTokenDefinitionParticle;
import com.radixdlt.identifiers.RRI;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.Test;

public class MintTokensActionMapperTest {
	@Test
	public void when_a_mint_action_is_created_with_zero_unallocated_tokens__then_a_overmint_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		RRI token = mock(RRI.class);
		MintTokensAction mintTokensAction = MintTokensAction.create(token, mock(RadixAddress.class), BigDecimal.ONE);
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(token);

		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.of(tokenDefinitionParticle)))
			.isInstanceOf(TokenOverMintException.class);
	}

	@Test
	public void when_a_mint_action_is_created_with_zero_particles__then_an_unknown_token_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		MintTokensAction mintTokensAction = MintTokensAction.create(mock(RRI.class), mock(RadixAddress.class), BigDecimal.ONE);
		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.empty()))
			.isInstanceOf(UnknownTokenException.class);
	}

	@Test
	public void when_a_mint_action_is_created_with_no_corresponding_token_definition__then_an_unknown_token_exception_should_be_thrown() {
		MintTokensActionMapper mapper = new MintTokensActionMapper();
		MintTokensAction mintTokensAction = MintTokensAction.create(mock(RRI.class), mock(RadixAddress.class), BigDecimal.ONE);
		MutableSupplyTokenDefinitionParticle tokenDefinitionParticle = mock(MutableSupplyTokenDefinitionParticle.class);
		when(tokenDefinitionParticle.getRRI()).thenReturn(mock(RRI.class));

		assertThatThrownBy(() -> mapper.mapToParticleGroups(mintTokensAction, Stream.of(tokenDefinitionParticle)))
			.isInstanceOf(UnknownTokenException.class);
	}
}