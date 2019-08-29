package com.radixdlt.atommodel.routines;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine.UsedAmount;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.utils.UInt256;
import org.junit.Test;

public class CreateFungibleTransitionRoutineTest {
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

	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidator.class)
		).getProcedure0();

		assertThat(procedure.inputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.ONE),
			null
		)).isEmpty();


		assertThat(procedure.outputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.ONE),
			null
		)).isEmpty();
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_execution_should_pop_output_and_one_left_on_input() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidator.class)
		).getProcedure0();


		assertThat(procedure.inputUsed(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.ONE),
			null
		)).get().isEqualTo(new UsedAmount(UInt256.ONE));

		assertThat(procedure.outputUsed(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.ONE),
			null
		)).isEmpty();
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidator.class)
		).getProcedure0();

		assertThat(procedure.inputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			null
		)).isEmpty();

		assertThat(procedure.outputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			null
		)).get().isEqualTo(new UsedAmount(UInt256.ONE));
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidator.class)
		).getProcedure0();

		assertThat(procedure.inputUsed(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.TWO),
			null
		)).isEmpty();

		assertThat(procedure.outputUsed(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.TWO),
			null
		)).isEmpty();
	}

	@Test
	public void when_validating_a_one_to_two_one_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, UsedAmount> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidator.class)
		).getProcedure2();

		assertThat(procedure.inputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			new UsedAmount(UInt256.ONE)
		)).isEmpty();

		assertThat(procedure.outputUsed(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			new UsedAmount(UInt256.ONE)
		)).isEmpty();
	}
}