package com.radixdlt.atomos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import com.radixdlt.utils.UInt256;
import java.util.Stack;
import org.junit.Test;

public class FungibleParticlesProcedureBuilderTest {
	private static class Fungible extends Particle {
		private final UInt256 amount;
		Fungible(UInt256 amount) {
			this.amount = amount;
		}

		public UInt256 getAmount() {
			return amount;
		}
	}

	private static class Fungible2 extends Particle {
		private final UInt256 amount;
		Fungible2(UInt256 amount) {
			this.amount = amount;
		}

		public UInt256 getAmount() {
			return amount;
		}
	}


	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.of(Fungible.class, Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);
		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		boolean succeed = procedure.inputExecute(new Fungible(UInt256.ONE), mock(AtomMetadata.class), stack);

		assertThat(succeed).isTrue();
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_validation_should_fail() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.of(Fungible.class, Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);
		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		boolean succeed = procedure.inputExecute(new Fungible(UInt256.TWO), mock(AtomMetadata.class), stack);

		assertThat(succeed).isFalse();
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.of(Fungible.class, Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);

		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		boolean succeed = procedure.inputExecute(new Fungible(UInt256.ONE), mock(AtomMetadata.class), stack);

		assertThat(succeed).isTrue();
		assertThat(stack).hasSize(1);
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.of(Fungible.class, Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);
		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));

		boolean succeed = procedure.inputExecute(new Fungible(UInt256.TWO), mock(AtomMetadata.class), stack);

		assertThat(succeed).isTrue();
		assertThat(stack).isEmpty();
	}

	@Test
	public void when_validating_a_reversed_one_way_transfer__then_input_should_fail() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.of(Fungible.class, Fungible::getAmount)
					.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.add(Fungible2.class,
				new FungibleDefinition.Builder<Fungible2>()
					.of(Fungible2.class, Fungible2::getAmount)
					.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible2.class);

		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));

		boolean succeed = procedure.inputExecute(new Fungible2(UInt256.ONE), mock(AtomMetadata.class), stack);

		assertThat(succeed).isFalse();
	}
}