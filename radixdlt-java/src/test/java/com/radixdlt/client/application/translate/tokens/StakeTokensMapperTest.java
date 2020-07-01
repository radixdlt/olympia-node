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

import com.google.common.collect.ImmutableMap;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.client.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StakeTokensMapperTest {

	@Test
	public void when_staking_tokens_without_funds__then_error_is_not_thrown() {
		RadixAddress address = mock(RadixAddress.class);

		RRI token = mock(RRI.class);
		when(token.getName()).thenReturn("TEST");

		StakeTokensAction action = mock(StakeTokensAction.class);
		when(action.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(action.getFrom()).thenReturn(address);
		when(action.getRRI()).thenReturn(token);

		StakeTokensMapper transferTranslator = new StakeTokensMapper();

		assertThat(transferTranslator.requiredState(action))
			.containsExactly(ShardedParticleStateId.of(TransferrableTokensParticle.class, address));

		assertThatThrownBy(() -> transferTranslator.mapToParticleGroups(action, Stream.empty()))
			.isEqualTo(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

	@Test
	public void when_staking_tokens_with_funds__then_error_is_not_thrown() {
		RadixAddress address1 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");

		RRI token = RRI.of(address1, "COOKIE");

		StakeTokensAction action = mock(StakeTokensAction.class);
		when(action.getAmount()).thenReturn(new BigDecimal(1));
		when(action.getFrom()).thenReturn(address1);
		when(action.getRRI()).thenReturn(token);

		StakeTokensMapper transferTranslator = new StakeTokensMapper();

		transferTranslator.mapToParticleGroups(action, Stream.of(
			new TransferrableTokensParticle(
				UInt256.MAX_VALUE,
				UInt256.ONE,
				address1,
				0,
				token,
				0,
				ImmutableMap.of()
			)
		));
	}

}