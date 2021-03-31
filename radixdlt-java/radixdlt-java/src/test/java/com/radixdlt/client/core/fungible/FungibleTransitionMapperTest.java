package com.radixdlt.client.core.fungible;


import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.identifiers.EUID;
import com.radixdlt.utils.UInt256;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class FungibleTransitionMapperTest {
	private static class TestParticle extends Particle {
		private final UInt256 amt;

		TestParticle(UInt256 amt) {
			this.amt = amt;
		}

		@Override
		public Set<EUID> getDestinations() {
			return ImmutableSet.of();
		}

		UInt256 getAmt() {
			return amt;
		}

		@Override
		public int hashCode() {
			return amt.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof TestParticle)) {
				return false;
			}

			return ((TestParticle) o).amt.equals(amt);
		}
	}

	private FungibleTransitionMapper<TestParticle, TestParticle> mapper;

	@Before
	public void setup() {
		this.mapper = new FungibleTransitionMapper<>(
			TestParticle::getAmt,
			TestParticle::new,
			TestParticle::new
		);
	}

	@Test
	public void when_creating_a_transition_with_not_enough_input__an_exception_should_be_thrown() {
		assertThatThrownBy(() -> this.mapper.mapToParticles(
			ImmutableList.of(new TestParticle(UInt256.ONE)),
			UInt256.TWO)
		)
			.isInstanceOf(NotEnoughFungiblesException.class)
			.matches(e -> {
				NotEnoughFungiblesException notEnoughFungiblesException = (NotEnoughFungiblesException) e;
				return notEnoughFungiblesException.getCurrent().equals(UInt256.ONE)
					&& notEnoughFungiblesException.getRequested().equals(UInt256.TWO);
			});
	}
}