package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.utils.UInt256;
import java.util.Map;
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
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
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
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
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
	public void when_validating_a_one_to_two_transfer__then_validation_should_fail() {
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isNotEmpty();
	}

	@Test
	public void when_validating_a_two_to_two_transfer__then_validation_should_succeed() {
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.down(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isEmpty();
	}

	@Test
	public void when_validating_an_initial_with_fungible_with_missing_initial__then_validation_should_fail() {
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.initialWith(Fungible2.class, (a, b, c) -> Result.success())
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.up(mock(Fungible.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isNotEmpty();
	}


	@Test
	public void when_validating_an_initial_with_fungible_with_initial__then_validation_should_succeed() {
		Map<Class<? extends Particle>, FungibleTransition<? extends Particle>> transitions = ImmutableMap.of(
			Fungible.class,
			FungibleTransition.<Fungible>build()
				.to(Fungible.class, f -> UInt256.ONE)
				.initialWith(Fungible2.class, (a, b, c) -> Result.success())
				.from(Fungible.class, (a, b) -> Result.success(), (a, b) -> true)
				.build()
		);

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(transitions);
		Stream<ProcedureError> errs = procedure.validate(
			ParticleGroup.of(
				SpunParticle.up(mock(Fungible.class)),
				SpunParticle.up(mock(Fungible2.class))
			),
			mock(AtomMetadata.class)
		);

		assertThat(errs).isEmpty();
	}
}