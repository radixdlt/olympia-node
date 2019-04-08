package com.radixdlt.client.core.fungible;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.radixdlt.client.core.fungible.FungibleParticleTransitioner.FungibleParticleTransition;
import java.util.Collections;
import org.junit.Test;
import org.radix.utils.UInt256;

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
	public void when_creating_a_transition_from_one_to_one__then_transitioned_should_be_one_migrated_should_be_empty_removed_should_be_one() {
		FungibleParticleTransition<TestParticle, TestParticle> transition =
			transitioner.createTransition(Collections.singletonList(new TestParticle(UInt256.ONE)), UInt256.ONE);
		assertThat(transition.getTransitioned()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getMigrated()).asList().isEmpty();
		assertThat(transition.getRemoved()).asList().containsExactly(new TestParticle(UInt256.ONE));
	}

	@Test
	public void when_creating_a_transition_from_two_to_one__then_transitioned_should_be_one_migrated_should_be_one_removed_should_be_a_two() {
		FungibleParticleTransition<TestParticle, TestParticle> transition =
			transitioner.createTransition(Collections.singletonList(new TestParticle(UInt256.TWO)), UInt256.ONE);
		assertThat(transition.getTransitioned()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getMigrated()).asList().containsExactly(new TestParticle(UInt256.ONE));
		assertThat(transition.getRemoved()).asList().containsExactly(new TestParticle(UInt256.TWO));
	}
}