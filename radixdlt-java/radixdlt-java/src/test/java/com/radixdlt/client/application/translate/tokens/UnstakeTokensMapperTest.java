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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.application.TokenUnitConversions;
import com.radixdlt.client.application.translate.ShardedParticleStateId;
import com.radixdlt.atommodel.tokens.StakedTokensParticle;
import com.radixdlt.atommodel.tokens.TransferrableTokensParticle;
import com.radixdlt.atom.SpunParticle;
import com.radixdlt.constraintmachine.Spin;
import com.radixdlt.identifiers.RRI;
import com.radixdlt.identifiers.RadixAddress;
import com.radixdlt.utils.UInt256;

import org.assertj.core.api.Condition;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UnstakeTokensMapperTest {

	@Test
	public void when_unstaking_tokens_without_funds__then_error_is_not_thrown() {
		RadixAddress address = mock(RadixAddress.class);

		RRI token = mock(RRI.class);
		when(token.getName()).thenReturn("TEST");

		UnstakeTokensAction action = mock(UnstakeTokensAction.class);
		when(action.getAmount()).thenReturn(new BigDecimal("1.0"));
		when(action.getFrom()).thenReturn(address);
		when(action.getRRI()).thenReturn(token);

		UnstakeTokensMapper transferTranslator = new UnstakeTokensMapper();

		assertThat(transferTranslator.requiredState(action))
			.containsExactly(ShardedParticleStateId.of(StakedTokensParticle.class, address));

		assertThatThrownBy(() -> transferTranslator.mapToParticleGroups(action, Stream.empty()))
			.isEqualTo(new InsufficientFundsException(token, BigDecimal.ZERO, new BigDecimal("1.0")));
	}

	@Test
	public void when_unstaking_tokens_with_funds__then_error_is_not_thrown() {
		RadixAddress address1 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RadixAddress address2 = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");

		RRI token = RRI.of(address1, "COOKIE");

		UnstakeTokensAction action = mock(UnstakeTokensAction.class);
		when(action.getAmount()).thenReturn(new BigDecimal(1));
		when(action.getFrom()).thenReturn(address1);
		when(action.getRRI()).thenReturn(token);
		when(action.getDelegate()).thenReturn(address2);

		UnstakeTokensMapper transferTranslator = new UnstakeTokensMapper();

		transferTranslator.mapToParticleGroups(action, Stream.of(
			new StakedTokensParticle(
				address2,
				address1,
				UInt256.MAX_VALUE,
				UInt256.ONE,
				token,
				ImmutableMap.of(),
				0L
			)
		));
	}

	@Test
	public void when_partial_unstaking_tokens__amounts_are_correct() {
		RadixAddress address1 = RadixAddress.from("JEbhKQzBn4qJzWJFBbaPioA2GTeaQhuUjYWkanTE6N8VvvPpvM8");
		RadixAddress address2 = RadixAddress.from("23B6fH3FekJeP6e5guhZAk6n9z4fmTo5Tngo3a11Wg5R8gsWTV2x");
		RRI token = RRI.of(address1, "COOKIE");
		UnstakeTokensAction action = UnstakeTokensAction.create(
			TokenUnitConversions.subunitsToUnits(3L),
			token,
			address1,
			address2
		);
		UnstakeTokensMapper transferTranslator = new UnstakeTokensMapper();

		final var result = transferTranslator.mapToParticleGroups(action, Stream.of(
			new StakedTokensParticle(
				address2,
				address1,
				UInt256.TEN,
				UInt256.ONE,
				token,
				ImmutableMap.of(),
				0L
			)
		));

		assertThat(result).hasSize(1);
		final var particleGroup = result.get(0);
		final var spunParticles = particleGroup.spunParticles().collect(ImmutableList.toImmutableList());
		assertThat(spunParticles)
			.hasSize(3)
			.haveExactly(1, stakedTokens(Spin.DOWN, UInt256.TEN))
			.haveExactly(1, stakedTokens(Spin.UP, UInt256.SEVEN))
			.haveExactly(1, transferrableTokens(Spin.UP, UInt256.THREE));
	}

	private Condition<SpunParticle> stakedTokens(Spin spin, UInt256 amount) {
		return givenParticle(spin, StakedTokensParticle.class, StakedTokensParticle::getAmount, amount);
	}

	private Condition<SpunParticle> transferrableTokens(Spin spin, UInt256 amount) {
		return givenParticle(spin, TransferrableTokensParticle.class, TransferrableTokensParticle::getAmount, amount);
	}

	private <T> Condition<SpunParticle> givenParticle(Spin spin, Class<T> cls, Function<T, UInt256> getAmount, UInt256 amount) {
		final var condition = new Condition<SpunParticle>() {
			@Override
			public boolean matches(SpunParticle value) {
				final var particle = value.getParticle();
				if (!value.getSpin().equals(spin) || !cls.isAssignableFrom(particle.getClass())) {
					return false;
				}
				UInt256 particleAmount = getAmount.apply(cls.cast(particle));
				return amount.equals(particleAmount);
			}
		};
		return condition.describedAs("%s %s with amount %s", spin, cls.getSimpleName(), amount);
	}


}