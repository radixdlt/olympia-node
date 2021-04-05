/*
 * (C) Copyright 2020 Radix DLT Ltd
 *
 * Radix DLT Ltd licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the
 * License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.  See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.radixdlt.atommodel.routines;

import nl.jqno.equalsverifier.EqualsVerifier;

import java.util.Objects;

import org.junit.Test;

import com.radixdlt.atommodel.routines.CreateFungibleTransitionRoutine.UsedAmount;
import com.radixdlt.atomos.Result;
import com.radixdlt.constraintmachine.Particle;
import com.radixdlt.constraintmachine.TransitionProcedure;
import com.radixdlt.constraintmachine.VoidUsedData;
import com.radixdlt.constraintmachine.WitnessValidator;
import com.radixdlt.utils.UInt256;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class CreateFungibleTransitionRoutineTest {

	private static final class Fungible extends Particle {
		private final UInt256 amount;
		Fungible(UInt256 amount) {
			this.amount = amount;
		}

		UInt256 getAmount() {
			return amount;
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.amount);
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof Fungible)) {
				return false;
			}
			final var that = (Fungible) obj;
			return Objects.equals(this.amount, that.amount);
		}

		@Override
		public String toString() {
			return "Fungible " + amount;
		}
	}

	interface WitnessValidatorFungible extends WitnessValidator<Fungible> {
		// Empty
	}

	@Test
	public void equalsContract() {
		EqualsVerifier.forClass(CreateFungibleTransitionRoutine.UsedAmount.class)
				.verify();
	}

	@Test
	public void when_underflowing_input__then_arithmetic_exception_thrown() {
		TransitionProcedure<Fungible, UsedAmount, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidatorFungible.class)
		).getProcedure1();

		assertThatThrownBy(() -> procedure.inputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			new UsedAmount(UInt256.TWO),
			new Fungible(UInt256.ZERO),
			null
		)).isInstanceOf(ArithmeticException.class).hasMessage("underflow").hasNoCause();

		assertThatThrownBy(() -> procedure.outputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			new UsedAmount(UInt256.TWO),
			new Fungible(UInt256.ZERO),
			null
		)).isInstanceOf(ArithmeticException.class).hasMessage("underflow").hasNoCause();
	}

	@Test
	public void when_underflowing_output__then_arithmetic_exception_thrown() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, UsedAmount> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidatorFungible.class)
		).getProcedure2();

		assertThatThrownBy(() -> procedure.inputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.ONE),
			new UsedAmount(UInt256.TWO)
		)).isInstanceOf(ArithmeticException.class).hasMessage("underflow").hasNoCause();

		assertThatThrownBy(() -> procedure.outputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.ONE),
			new UsedAmount(UInt256.TWO)
		)).isInstanceOf(ArithmeticException.class).hasMessage("underflow").hasNoCause();
	}

	@Test
	public void when_validating_a_simple_fungible_transfer__then_validation_should_succeed() {
		TransitionProcedure<Fungible, VoidUsedData, Fungible, VoidUsedData> procedure = new CreateFungibleTransitionRoutine<>(
			Fungible.class, Fungible.class, Fungible::getAmount, Fungible::getAmount,
			(a, b) -> Result.success(),
			mock(WitnessValidatorFungible.class)
		).getProcedure0();

		assertThat(procedure.inputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.ONE),
			null
		)).isEmpty();


		assertThat(procedure.outputUsedCompute().compute(
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
			mock(WitnessValidatorFungible.class)
		).getProcedure0();


		assertThat(procedure.inputUsedCompute().compute(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.ONE),
			null
		)).get().isEqualTo(new UsedAmount(UInt256.ONE));

		assertThat(procedure.outputUsedCompute().compute(
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
			mock(WitnessValidatorFungible.class)
		).getProcedure0();

		assertThat(procedure.inputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			null
		)).isEmpty();

		assertThat(procedure.outputUsedCompute().compute(
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
			mock(WitnessValidatorFungible.class)
		).getProcedure0();

		assertThat(procedure.inputUsedCompute().compute(
			new Fungible(UInt256.TWO),
			null,
			new Fungible(UInt256.TWO),
			null
		)).isEmpty();

		assertThat(procedure.inputUsedCompute().compute(
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
			mock(WitnessValidatorFungible.class)
		).getProcedure2();

		assertThat(procedure.inputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			new UsedAmount(UInt256.ONE)
		)).isEmpty();

		assertThat(procedure.outputUsedCompute().compute(
			new Fungible(UInt256.ONE),
			null,
			new Fungible(UInt256.TWO),
			new UsedAmount(UInt256.ONE)
		)).isEmpty();
	}
}