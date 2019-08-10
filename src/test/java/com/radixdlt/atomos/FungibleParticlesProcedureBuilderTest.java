package com.radixdlt.atomos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.radixdlt.atoms.Particle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ParticleProcedure;
import com.radixdlt.constraintmachine.ParticleProcedure.ProcedureResult;
import com.radixdlt.utils.UInt256;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class FungibleParticlesProcedureBuilderTest {
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
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			mock(AtomMetadata.class)
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_two_to_one_transfer__then_execution_should_pop_output_and_one_left_on_input() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);

		AtomicReference<Object> inputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.TWO),
			inputData,
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			mock(AtomMetadata.class)
		);

		assertThat(inputData).hasValue(UInt256.ONE);
		assertThat(result).isEqualTo(ProcedureResult.POP_OUTPUT);
	}

	@Test
	public void when_validating_a_one_to_two_transfer__then_input_should_succeed_and_one_left_on_stack() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);

		AtomicReference<Object> outputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			outputData,
			mock(AtomMetadata.class)
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT);
		assertThat(outputData).hasValue(UInt256.ONE);
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);

		AtomicReference<Object> outputData = new AtomicReference<>();
		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.TWO),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			outputData,
			mock(AtomMetadata.class)
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_one_to_two_one_transfer__then_input_should_succeed_and_zero_left_on_stack() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible.class);

		ProcedureResult result = procedure.execute(
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.TWO),
			new AtomicReference<>(UInt256.ONE),
			mock(AtomMetadata.class)
		);

		assertThat(result).isEqualTo(ProcedureResult.POP_INPUT_OUTPUT);
	}

	@Test
	public void when_validating_a_reversed_one_way_transfer__then_input_should_fail() {
		ParticleProcedure procedure = new FungibleParticlesProcedureBuilder()
			.add(Fungible.class,
				new FungibleDefinition.Builder<Fungible>()
					.amountMapper(Fungible::getAmount)
					.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.add(Fungible2.class,
				new FungibleDefinition.Builder<Fungible2>()
					.amountMapper(Fungible2::getAmount)
					.to(Fungible2.class, (a, b) -> Result.success(), (a, b) -> true)
					.build()
			)
			.build()
			.get(Fungible2.class);

		ProcedureResult result = procedure.execute(
			new Fungible2(UInt256.ONE),
			new AtomicReference<>(),
			new Fungible(UInt256.ONE),
			new AtomicReference<>(),
			mock(AtomMetadata.class)
		);

		assertThat(result).isEqualTo(ProcedureResult.ERROR);
	}
}