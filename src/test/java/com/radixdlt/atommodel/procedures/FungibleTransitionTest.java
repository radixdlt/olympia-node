package com.radixdlt.atommodel.procedures;

import static org.assertj.core.api.Assertions.assertThat;

import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.ConstraintProcedure;
import com.radixdlt.constraintmachine.ConstraintProcedure.ProcedureResult;
import com.radixdlt.utils.UInt256;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class FungibleTransitionTest {
	private static class Fungible extends Particle {
		private final UInt256 amount;
		Fungible(UInt256 amount) {
			this.amount = amount;
		}

		UInt256 getAmount() {
			return amount;
		}

		@Override
		public String toString() {
			return "Fungible " + amount;
		}
	}

	private static class Fungible2 extends Particle {
		private final UInt256 amount;
		Fungible2(UInt256 amount) {
			this.amount = amount;
		}

		UInt256 getAmount() {
			return amount;
		}

		public String toString() {
			return "Fungible2 " + amount;
		}
	}


	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible.class, Fungible::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.ONE),
			new AtomicReference<>()
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_execution_should_pop_output_and_one_left_on_input() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible.class, Fungible::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);

		AtomicReference<Object> inputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.TWO),
			inputData,
			new Fungible(UInt256.ONE),
			new AtomicReference<>()
		);

		assertThat(inputData).hasValue(UInt256.ONE);
		assertThat(result).isEqualTo(ProcedureResult.POP_OUTPUT);
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible.class, Fungible::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);

		AtomicReference<Object> outputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			outputData
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT);
		assertThat(outputData).hasValue(UInt256.ONE);
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible.class, Fungible::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);

		AtomicReference<Object> outputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.TWO),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			outputData
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_one_to_two_one_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible.class, Fungible::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);

		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			new AtomicReference<>(UInt256.ONE)
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_reversed_one_way_transfer__then_input_should_fail() {
		ConstraintProcedure procedure = new FungibleTransition<>(
			Fungible.class, Fungible::getAmount,
			Fungible2.class, Fungible2::getAmount,
			(a, b) -> true, (a, b) -> Result.success()
		);

		assertThat(procedure.supports()).doesNotContain(Pair.of(Fungible2.class, Fungible.class));
	}
}