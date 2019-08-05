package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.radixdlt.atomos.FungibleComposition;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.mapper.ParticleToAmountMapper;
import com.radixdlt.atoms.Particle;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.EUID;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.constraintmachine.ProcedureError;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class FungibleTransitionConstraintProcedureTest {
	/*
	private static abstract class FungibleParticle extends Particle {
		public UInt256 getAmount() {
			throw new UnsupportedOperationException("Mock me!");
		}
	}

	private abstract static class Oxygen extends FungibleParticle {
	}

	private abstract static class Hydrogen extends FungibleParticle {
	}

	private abstract static class HydroNextGen extends Hydrogen {
	}

	private abstract static class Uranium extends FungibleParticle {
	}

	private abstract static class H2O extends FungibleParticle {
	}

	// I also know that H2O doesn't decay, but makes for a good test, so we're good
	private static final FungibleComposition COMPOSITION_H2O = FungibleComposition.of(
		2, Hydrogen.class,
		1, Oxygen.class
	);

	// Uranium actually *does* decay, at least the unstable variants do - so much for accurate physics in unit tests.
	private static final FungibleComposition COMPOSITION_URANIUM_DECAY = FungibleComposition.of(
		2, Uranium.class
	);

	private static final FungibleComposition COMPOSITION_URANIUM_STABLE = FungibleComposition.of(
		1, Uranium.class
	);

	private static <T extends Particle> Fungible mockFungible(Class<T> cls, int index, EUID hid, int amount, List<String> shouldBeContainedStrings) {
		T particle = mock(cls);
		when(particle.getHID()).thenReturn(hid);
		Fungible fungible = new Fungible(particle, cls, UInt256.from(amount), index);

		shouldBeContainedStrings.add(cls.getSimpleName());
		shouldBeContainedStrings.add(String.valueOf(index));
		shouldBeContainedStrings.add(String.valueOf(amount));
		shouldBeContainedStrings.add(String.valueOf(hid));

		return fungible;
	}

	private static <T extends FungibleParticle> FungibleParticle mockFungibleParticle(Class<T> cls, int amount) {
		T particle = mock(cls);
		when(particle.getAmount()).thenReturn(UInt256.from(amount));
		return particle;
	}

	private FungibleTransition<Uranium> buildUraniumDecayAndStableTransition() {
		FungibleTransitionMember<Uranium> memberUranium = new FungibleTransitionMember<>(
			Uranium.class, (from, to, meta) -> Result.success());
		return FungibleTransition.from(
			Uranium.class,
			Uranium::getAmount,
			(u0, u1) -> true,
			Arrays.asList(
				FungibleFormula.from(
					Stream.of(
						memberUranium
					),
					COMPOSITION_URANIUM_DECAY
				),
				FungibleFormula.from(
					Stream.of(
						memberUranium
					),
					COMPOSITION_URANIUM_STABLE
				)
			)
		);
	}

	private FungibleTransition<Uranium> buildUraniumDecayTransition() {
		FungibleTransitionMember<Uranium> memberUranium = new FungibleTransitionMember<>(
			Uranium.class, (from, to, meta) -> Result.success());
		return FungibleTransition.from(
			Uranium.class,
			Uranium::getAmount,
			(u0, u1) -> true,
			Arrays.asList(
				FungibleFormula.from(
					Stream.of(
						memberUranium
					),
					COMPOSITION_URANIUM_DECAY
				)
			)
		);
	}

	private FungibleTransition<Uranium> buildUraniumStableTransition() {
		FungibleTransitionMember<Uranium> memberUranium = new FungibleTransitionMember<>(
			Uranium.class, (from, to, meta) -> Result.success());
		return FungibleTransition.from(
			Uranium.class,
			Uranium::getAmount,
			(u0, u1) -> true,
			Arrays.asList(
				FungibleFormula.from(
					Stream.of(
						memberUranium
					),
					COMPOSITION_URANIUM_STABLE
				)
			)
		);
	}

	private FungibleTransition<H2O> buildH2OFungibleTransition() {
		FungibleTransitionMember<Hydrogen> memberHydrogen = new FungibleTransitionMember<>(
			Hydrogen.class, (from, to, meta) -> Result.success());
		FungibleTransitionMember<Oxygen> memberOxygen = new FungibleTransitionMember<>(
			Oxygen.class, (from, to, meta) -> Result.success());

		return FungibleTransition.from(
			H2O.class,
			H2O::getAmount,
			(h0, h1) -> true,
			Arrays.asList(
				FungibleFormula.from(
					Stream.of(
						memberHydrogen,
						memberOxygen
					),
					COMPOSITION_H2O
				)
			)
		);
	}

	private FungibleTransition<Hydrogen> buildHydrogenTransition() {
		return FungibleTransition.from(Hydrogen.class, Hydrogen::getAmount, (h0, h1) -> true, Lists.newArrayList(), (to, meta) -> Result.error(""));
	}

	private FungibleTransition<Oxygen> buildOxygenTransition() {
		return FungibleTransition.from(Oxygen.class, Oxygen::getAmount, (o0, o1) -> true, Lists.newArrayList(), (to, meta) -> Result.error(""));
	}

	@Test
	public void testForeignTypesInTransitions() {
		assertThatThrownBy(() -> new FungibleTransitionConstraintProcedure(Arrays.asList(
			buildH2OFungibleTransition()
		)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Foreign input types")
			.hasMessageContaining("Oxygen")
			.hasMessageContaining("Hydrogen");
	}

	@Test
	public void testValidUraniumDecayTransition() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumDecayTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildOxygenTransition(),
			buildHydrogenTransition()
		));
		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 8), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 3), Spin.UP);
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 1), Spin.UP);

		procedure.validate(groupBuilder.build(), mock(AtomMetadata.class));
	}

	@Test
	public void testValidUraniumStableTransition() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumStableTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildOxygenTransition(),
			buildHydrogenTransition()
		));
		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 8), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 4), Spin.UP);
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 3), Spin.UP);
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 1), Spin.UP);

		procedure.validate(groupBuilder.build(), mock(AtomMetadata.class));
	}

	@Test
	@Ignore("Greedy matching can't handle this yet. Revisit if necessary.")
	public void testValidUraniumDecayAndStableTransition() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumDecayAndStableTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildOxygenTransition(),
			buildHydrogenTransition()
		));

		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 10), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 3), Spin.UP);
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 1), Spin.UP);
		groupBuilder.addParticle(mockFungibleParticle(Uranium.class, 2), Spin.UP);

		// TODO What should happen here? This currently fails because of greedy matching and there is no way for
		// TODO the user to override the order of matching formulas to their preference as this is not affected
		// TODO by particle order and match order is deliberately out of user control.
		// procedure.toValidator().validate(atom, mock(StateProvider.class));
	}

	@Test
	public void testValidH2OTransition() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumDecayTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildHydrogenTransition(),
			buildOxygenTransition()
		));

		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 10), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 2), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 20), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 4), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(H2O.class, 12), Spin.UP);

		procedure.validate(groupBuilder.build(), mock(AtomMetadata.class));
	}

	@Test
	public void testInvalidH2OTransitionWithUnspentConsumables() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumDecayTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildHydrogenTransition(),
			buildOxygenTransition()
		));

		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 10), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 2), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 20), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 4), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 5), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(H2O.class, 12), Spin.UP);

		Stream<ProcedureError> issues = procedure.validate(groupBuilder.build(), mock(AtomMetadata.class));
		assertThat(issues).anyMatch(i -> i.getErrMsg().contains("5 unspent"));
	}

	@Test
	public void testInvalidH2OTransitionWithUnsatisfiedConsumers() {
		FungibleTransition<H2O> transitionH2O = buildH2OFungibleTransition();
		FungibleTransition<Uranium> transitionUraniumDecay = buildUraniumDecayTransition();
		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transitionH2O,
			transitionUraniumDecay,
			buildHydrogenTransition(),
			buildOxygenTransition()
		));

		ParticleGroup.ParticleGroupBuilder groupBuilder = ParticleGroup.builder();
		// consumables
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 6), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Oxygen.class, 1), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 20), Spin.DOWN);
		groupBuilder.addParticle(mockFungibleParticle(Hydrogen.class, 4), Spin.DOWN);
		// consumers
		groupBuilder.addParticle(mockFungibleParticle(H2O.class, 12), Spin.UP);

		Stream<ProcedureError> issues = procedure.validate(groupBuilder.build(), mock(AtomMetadata.class));
		assertThat(issues).anyMatch(i -> i.getErrMsg().contains("5 unsatisfied"));
	}

	@Test
	public void testCreate() {
		assertThatThrownBy(() -> new FungibleTransitionConstraintProcedure(null))
			.isInstanceOf(NullPointerException.class);

		FungibleTransition<H2O> transition0 = mock(FungibleTransition.class);
		when(transition0.getOutputParticleClass()).thenReturn(H2O.class);
		when(transition0.getOutputParticleToAmountMapper()).thenReturn(mock(ParticleToAmountMapper.class));
		when(transition0.getAllInputs()).thenReturn(ImmutableMap.of(
			Oxygen.class, Collections.singletonList(mock(FungibleTransitionMember.class)),
			Hydrogen.class, Collections.singletonList(mock(FungibleTransitionMember.class))
		));
		FungibleTransition<Uranium> transition1 = mock(FungibleTransition.class);
		when(transition1.getOutputParticleClass()).thenReturn(Uranium.class);
		when(transition1.getOutputParticleToAmountMapper()).thenReturn(mock(ParticleToAmountMapper.class));
		when(transition1.getAllInputs()).thenReturn(ImmutableMap.of(
			Uranium.class, Collections.singletonList(mock(FungibleTransitionMember.class))
		));

		FungibleTransitionConstraintProcedure procedure = new FungibleTransitionConstraintProcedure(Arrays.asList(
			transition0,
			transition1,
			buildOxygenTransition(),
			buildHydrogenTransition()
		));

		assertThat(procedure.getInputTypes()).containsExactlyInAnyOrder(
			Oxygen.class,
			Hydrogen.class,
			Uranium.class
		);
		assertThat(procedure.getOutputTypes()).containsExactlyInAnyOrder(
			H2O.class,
			Uranium.class,
			Hydrogen.class,
			Oxygen.class
		);
	}

	@Test
	public void testCheckAllRequiresSatisfiedSuccessWhenSatisfied() {
		Assert.assertTrue(FungibleTransitionConstraintCheck.checkAllRequiresSatisfied(
			Lists.newArrayList(),
			FungibleOutputs.of(),
			FungibleOutputs.of(),
			FungibleInputs.of(),
			FungibleInputs.of(),
			Lists.newArrayList(),
			mock(ParticleValueMapper.class)).isSuccess());
	}

	@Test
	public void testCheckAllRequiresSatisfiedErrorsOnViolation() {
		Assert.assertTrue(FungibleTransitionConstraintCheck.checkAllRequiresSatisfied(
			Lists.newArrayList(),
			FungibleOutputs.of(Stream.of(new Fungible(mock(Particle.class), UInt256.ONE, 0))),
			FungibleOutputs.of(),
			FungibleInputs.of(),
			FungibleInputs.of(),
			Lists.newArrayList(),
			mock(ParticleValueMapper.class)).isError());

		Assert.assertTrue(FungibleTransitionConstraintCheck.checkAllRequiresSatisfied(
			Lists.newArrayList(),
			FungibleOutputs.of(),
			FungibleOutputs.of(),
			FungibleInputs.of(Stream.of(new Fungible(mock(Particle.class), UInt256.ONE, 0))),
			FungibleInputs.of(),
			Lists.newArrayList(),
			mock(ParticleValueMapper.class)).isError());
	}

	@Test
	public void testCheckAllRequiresSatisfiedContainsRelevantInfo() {
		List<String> shouldBeContained = new ArrayList<>();

		FungibleTransition<H2O> transition = mock(FungibleTransition.class);
		when(transition.getOutputParticleClass()).thenReturn(H2O.class);
		FungibleTransitionMatch match = mock(FungibleTransitionMatch.class);
		FungibleFormulaMatch formulaMatch = mock(FungibleFormulaMatch.class);
		Fungible consumer0 = mockFungible(H2O.class, 0, EUID.ZERO, 5, shouldBeContained);
		when(formulaMatch.getSatisfiedOutputs()).thenReturn(FungibleOutputs.of(Stream.of(
			consumer0
		)));
		Fungible consumable0 = mockFungible(Hydrogen.class, 0, EUID.ONE, 10, shouldBeContained);
		when(formulaMatch.getMatchedInputs()).thenReturn(FungibleInputs.of(Stream.of(
			consumable0
		)));
		Fungible consumer2 = mockFungible(Oxygen.class, 17, EUID.TWO, 7, shouldBeContained);
		when(match.getMatchedInitials()).thenReturn(FungibleOutputs.of(Stream.of(
			consumer2
		)));
		when(match.getTransition()).thenReturn(transition);
		when(match.getMachedFormulas()).thenReturn(ImmutableList.of(formulaMatch));
		when(match.hasMatch()).thenReturn(true);
		FungibleTransitionMatchResult matchResult = new FungibleTransitionMatchResult(
			transition,
			match,
			mock(FungibleTransitionMatchInformation.class)
		);

		Fungible consumer1 = mockFungible(H2O.class, 1, EUID.TWO, 5, shouldBeContained);

		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(consumable0));
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(consumer0));
		FungibleOutputs unsatisfiedFungibleOutputs = FungibleOutputs.of(Stream.of(consumer1));

		Result result = FungibleTransitionConstraintCheck.checkAllRequiresSatisfied(
			Collections.singletonList(transition),
			fungibleOutputs,
			unsatisfiedFungibleOutputs,
			fungibleInputs,
			FungibleInputs.of(),
			Collections.singletonList(matchResult),
			mock(ParticleValueMapper.class)
		);
		Assert.assertTrue(result.isError());

		String errorMsg = result.errorStream().findFirst().get();
		shouldBeContained.forEach(Assertions.assertThat(errorMsg)::contains);
	}

	@Test
	public void testGetFungibles() {
		Oxygen oxygen0 = mock(Oxygen.class);
		Hydrogen hydrogen0 = mock(Hydrogen.class);
		HydroNextGen hydroNextGen0 = mock(HydroNextGen.class);
		Uranium uranium0 = mock(Uranium.class);

		ParticleGroup group = ParticleGroup.of(
			Stream.of(oxygen0, hydrogen0, hydroNextGen0, uranium0)
				.map(SpunParticle::up)
				.collect(Collectors.toList())
		);

		ParticleValueMapper valueMapper = mock(ParticleValueMapper.class);
		when(valueMapper.amount(oxygen0)).thenReturn(UInt256.ONE);
		when(valueMapper.amount(hydrogen0)).thenReturn(UInt256.TWO);
		when(valueMapper.amount(hydroNextGen0)).thenReturn(UInt256.THREE);
		when(valueMapper.amount(uranium0)).thenThrow(new IllegalStateException("Should not check for unrelated instance"));

		Set<Class<? extends Particle>> relevantTypes = ImmutableSet.of(Oxygen.class, Hydrogen.class);
		Stream<Fungible> fungibles = FungibleTransitionConstraintProcedure.getFungibles(group, Spin.UP, relevantTypes, valueMapper);
		assertThat(fungibles).containsExactly(
			new Fungible(oxygen0, UInt256.ONE, 0),
			new Fungible(hydrogen0, UInt256.TWO, 1),
			new Fungible(hydroNextGen0, UInt256.THREE, 2)
		);
	}
	*/
}