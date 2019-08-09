package com.radixdlt.atomos.procedures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.FungibleDefinition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.utils.UInt256;
import java.util.Stack;
import java.util.stream.Stream;
import org.junit.Test;

public class FungibleTransitionConstraintProcedureTest {
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
	}


	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			new FungibleDefinition.Builder<Fungible>()
				.of(Fungible.class, f -> UInt256.ONE)
				.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isEmpty();
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_validation_should_fail() {
		ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			new FungibleDefinition.Builder<Fungible>()
				.of(Fungible.class, f -> UInt256.ONE)
				.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isNotEmpty();
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			new FungibleDefinition.Builder<Fungible>()
				.of(Fungible.class, f -> UInt256.ONE)
				.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(mock(Fungible.class), null));
		stack.push(Pair.of(mock(Fungible.class), null));
		boolean succeed = procedure.getProcedures().get(Fungible.class)
			.inputExecute(mock(Fungible.class), mock(AtomMetadata.class), stack);

		assertThat(succeed).isTrue();
		assertThat(stack).hasSize(1);
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			new FungibleDefinition.Builder<Fungible>()
				.of(Fungible.class, Fungible::getAmount)
				.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));
		stack.push(Pair.of(new Fungible(UInt256.ONE), null));

		boolean succeed = procedure.getProcedures().get(Fungible.class)
			.inputExecute(new Fungible(UInt256.TWO), mock(AtomMetadata.class), stack);

		assertThat(succeed).isTrue();
		assertThat(stack).isEmpty();
	}

	@Test
	public void when_validating_a_reversed_one_way_transfer__then_input_should_fail() {
		ImmutableMap<Class<? extends Particle>, FungibleDefinition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			new FungibleDefinition.Builder<Fungible>()
				.of(Fungible.class, f -> UInt256.ONE)
				.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
				.build(),
			Fungible2.class,
			new FungibleDefinition.Builder<Fungible2>()
				.of(Fungible2.class, f -> UInt256.ONE)
				.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		Stack<Pair<Particle, Object>> stack = new Stack<>();
		stack.push(Pair.of(mock(Fungible.class), null));

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		boolean succeed = procedure.getProcedures().get(Fungible2.class)
			.inputExecute(mock(Fungible2.class), mock(AtomMetadata.class), stack);

		assertThat(succeed).isFalse();
	}
}