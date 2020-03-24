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

package com.radixdlt.client.core.fungible;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import java.util.Collections;
import org.junit.Test;
import com.radixdlt.utils.UInt256;

public class FungibleParticleTransitionerTest {
	private static class TestParticle {
		private final UInt256 amt;

		TestParticle(UInt256 amt) {
			this.amt = amt;
		}

		UInt256 getAmt() {
			return amt;
		}

		public int hashCode() {
			return amt.hashCode();
		}

		public boolean equals(Object o) {
			if (!(o instanceof TestParticle)) {
				return false;
			}

			return ((TestParticle) o).amt.equals(amt);
		}
	}

	private	FungibleParticleTransitioner<TestParticle, TestParticle> transitioner = new FungibleParticleTransitioner<>(
		(amt, t) -> new TestParticle(amt),
		l -> l,
		(amt, t) -> new TestParticle(amt),
		l -> l,
		TestParticle::getAmt
	);

	@Test
	public void when_creating_a_transition_from_one_to_one__then_transitioned_should_be_one_migrated_should_be_empty_removed_should_be_one()
		throws NotEnoughFungiblesException {
		FungibleParticleTransition<TestParticle, TestParticle> transition =
			transitioner.createTransition(Collections.singletonList(new TestParticle(UInt256.ONE)), UInt256.ONE);
		assertThat(transition.getTransitioned()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getMigrated()).asList().isEmpty();
		assertThat(transition.getRemoved()).asList().containsExactly(new TestParticle(UInt256.ONE));
	}

	@Test
	public void when_creating_a_transition_from_two_to_one__then_transitioned_should_be_one_migrated_should_be_one_removed_should_be_a_two()
		throws NotEnoughFungiblesException {
		FungibleParticleTransition<TestParticle, TestParticle> transition =
			transitioner.createTransition(Collections.singletonList(new TestParticle(UInt256.TWO)), UInt256.ONE);
		assertThat(transition.getTransitioned()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getMigrated()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getRemoved()).asList().containsExactly(new TestParticle(UInt256.TWO));
	}

	@Test
	public void when_creating_a_transition_with_not_enough_input__an_exception_should_be_thrown() {
		assertThatThrownBy(() -> transitioner.createTransition(Collections.singletonList(new TestParticle(UInt256.ONE)), UInt256.TWO))
			.isInstanceOf(NotEnoughFungiblesException.class)
			.matches(e -> {
				NotEnoughFungiblesException notEnoughFungiblesException = (NotEnoughFungiblesException) e;
				return notEnoughFungiblesException.getCurrent().equals(UInt256.ONE)
					&& notEnoughFungiblesException.getRequested().equals(UInt256.TWO);
			});
	}
}