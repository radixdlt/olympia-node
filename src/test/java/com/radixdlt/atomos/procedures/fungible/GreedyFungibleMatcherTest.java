package com.radixdlt.atomos.procedures.fungible;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.google.common.collect.Maps;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.procedures.fungible.FungibleMatcher.FungibleMatcherResult;
import com.radixdlt.atomos.procedures.fungible.GreedyFungibleMatcher.FungibleFormulasMatcher;
import com.radixdlt.atomos.procedures.fungible.GreedyFungibleMatcher.FungibleTransitionMatcher;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.util.Lists;
import org.junit.Test;

public class GreedyFungibleMatcherTest {
	/*
	private static final FungibleComposition COMPOSITION_H2O = FungibleComposition.of(
		2, Hydrogen.class,
		1, Oxygen.class
	);
	*/

	@Test
	public void testCreate() {
		new GreedyFungibleMatcher();
		assertThatThrownBy(() -> new GreedyFungibleMatcher(null, mock(FungibleFormulasMatcher.class)))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new GreedyFungibleMatcher(mock(FungibleTransitionMatcher.class), null))
			.isInstanceOf(NullPointerException.class);
	}

	@Test
	public void testMatchNull() {
		assertThatThrownBy(() -> gfmatch(null, mock(FungibleOutputs.class), mock(FungibleInputs.class), mock(AtomMetadata.class)))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> gfmatch(Lists.newArrayList(), null, mock(FungibleInputs.class), mock(AtomMetadata.class)))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> gfmatch(Lists.newArrayList(), mock(FungibleOutputs.class), null, mock(AtomMetadata.class)))
			.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> gfmatch(Lists.newArrayList(), mock(FungibleOutputs.class), mock(FungibleInputs.class), null))
			.isInstanceOf(NullPointerException.class);
	}

	private FungibleMatcherResult gfmatch(
		List<FungibleTransition<?>> transitions,
		FungibleOutputs fungibleOutputs,
		FungibleInputs fungibleInputs,
		AtomMetadata metadata
	) {
		return new GreedyFungibleMatcher().match(transitions, fungibleOutputs, fungibleInputs, metadata);
	}

	@Test
	public void testMatch() {
		FungibleTransition h2OTransition = mock(FungibleTransition.class);
		when(h2OTransition.getOutputParticleClass()).thenReturn(H2O.class);
		FungibleTransition uraniumTransition = mock(FungibleTransition.class);
		when(uraniumTransition.getOutputParticleClass()).thenReturn(Uranium.class);

		Fungible outputH2O = new Fungible(mock(H2O.class), H2O.class, UInt256.ONE, 5);
		Fungible outputUranium = new Fungible(mock(Uranium.class), Uranium.class, UInt256.FOUR, 6);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			outputH2O,
			outputUranium
		));
		Fungible inputHydrogen = new Fungible(mock(Hydrogen.class), Hydrogen.class, UInt256.TEN, 0);
		Fungible inputOxygen = new Fungible(mock(Oxygen.class), Oxygen.class, UInt256.FIVE, 1);
		Fungible inputUranium = new Fungible(mock(Uranium.class), Uranium.class, UInt256.EIGHT, 2);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			inputHydrogen,
			inputUranium,
			inputOxygen // deliberately out of order to verify indexed sorting
		));
		AtomMetadata metadata = mock(AtomMetadata.class);
		FungibleFormulaMatch h2OMatch = new FungibleFormulaMatch(new InputsOutputsMatch(
			outputH2O,
			FungibleInputs.of(Stream.of(inputHydrogen, inputOxygen))
		));
		FungibleFormulaMatch uraniumMatch = new FungibleFormulaMatch(new InputsOutputsMatch(
			outputUranium,
			FungibleInputs.of(Stream.of(inputUranium))
		));
		FungibleTransitionMatch h20TransitionMatch = new FungibleTransitionMatch(
			h2OTransition,
			Collections.singletonList(h2OMatch),
			FungibleOutputs.of()
		);
		FungibleTransitionMatchResult h20TransitionMatchResult = new FungibleTransitionMatchResult(
			h2OTransition,
			h20TransitionMatch,
			mock(FungibleTransitionMatchInformation.class)
		);
		FungibleTransitionMatch uraniumTransitionMatch = new FungibleTransitionMatch(
			h2OTransition,
			Collections.singletonList(uraniumMatch),
			FungibleOutputs.of()
		);
		FungibleTransitionMatchResult uraniumTransitionMatchResult = new FungibleTransitionMatchResult(
			uraniumTransition,
			uraniumTransitionMatch,
			mock(FungibleTransitionMatchInformation.class)
		);

		GreedyFungibleMatcher matcher = new GreedyFungibleMatcher(
			(transition, transitioninputs, transitionoutputs, transitionMetadata, formulaMatcher) -> {
				assertEquals(metadata, transitionMetadata);

				// transitions must be checked in order
				if (transition == h2OTransition) {
					assertThat(transitionoutputs.fungibles()).containsExactly(
						outputH2O
					);
					assertThat(transitioninputs.fungibles()).containsExactly(
						inputHydrogen,
						inputOxygen,
						inputUranium
					);

					return h20TransitionMatchResult;
				} else if (transition == uraniumTransition) {
					assertThat(transitionoutputs.fungibles()).containsExactly(
						outputUranium
					);
					assertThat(transitioninputs.fungibles()).containsExactly(
						inputUranium
					);

					return uraniumTransitionMatchResult;
				}

				throw new IllegalStateException("Unexpected transition " + transition);
			},
			mock(FungibleFormulasMatcher.class));
		FungibleMatcher.FungibleMatcherResult matchResult =
			matcher.match(Arrays.asList(h2OTransition, uraniumTransition), fungibleOutputs, fungibleInputs, metadata);
		assertThat(matchResult.getMatches()).containsExactly(
			h20TransitionMatch,
			uraniumTransitionMatch
		);
	}

	@Test
	public void testMatchTransition() {
		FungibleTransition transition = mock(FungibleTransition.class);
		Fungible outputH2OComposed = new Fungible(mock(H2O.class), H2O.class, UInt256.FIVE, 4);
		Fungible outputH2ODecayed = new Fungible(mock(H2O.class), H2O.class, UInt256.ONE, 5);
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(Stream.of(
			outputH2OComposed,
			outputH2ODecayed
		));
		Fungible inputHydrogen = new Fungible(mock(Hydrogen.class), Hydrogen.class, UInt256.TEN, 0);
		Fungible inputOxygen = new Fungible(mock(Oxygen.class), Oxygen.class, UInt256.FIVE, 1);
		Fungible inputH2O = new Fungible(mock(H2O.class), H2O.class, UInt256.TWO, 2);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			inputHydrogen,
			inputOxygen,
			inputH2O
		));
		AtomMetadata metadata = mock(AtomMetadata.class);
		FungibleFormulaMatch h2OMatch = new FungibleFormulaMatch(new InputsOutputsMatch(
			outputH2OComposed,
			FungibleInputs.of(Stream.of(inputHydrogen, inputOxygen))
		));
		FungibleFormulaMatchResult h2OMatchResult = new FungibleFormulaMatchResult(h2OMatch, mock(FungibleFormulaMatchInformation.class));
		FungibleFormulaMatch h2ODecayMatch = new FungibleFormulaMatch(new InputsOutputsMatch(
			outputH2ODecayed,
			FungibleInputs.of(Stream.of(inputH2O))
		));
		FungibleFormulaMatchResult h2ODecayMatchResult = new FungibleFormulaMatchResult(h2ODecayMatch, mock(FungibleFormulaMatchInformation.class));

		FungibleTransitionMatchResult transitionMatchResult = GreedyFungibleMatcher
			.matchTransition(transition, fungibleInputs, fungibleOutputs, metadata,
			(possibleFormulas, formulainputs, output, formulaMetadata) -> {
				assertEquals(metadata, formulaMetadata);
				// fungibleOutputs must be checked in order
				if (output.equals(outputH2OComposed)) {
					assertThat(formulainputs.fungibles()).containsExactly(
						inputHydrogen,
						inputOxygen,
						inputH2O
					);

					return Collections.singletonList(h2OMatchResult);
				} else if (output.equals(outputH2ODecayed)) {
					assertThat(formulainputs.fungibles()).containsExactly(
						inputH2O
					);

					return Collections.singletonList(h2ODecayMatchResult);
				}

				throw new IllegalStateException("Unknown output " + output);
			}
		);
		assertEquals(transition, transitionMatchResult.getTransition());
		assertThat(transitionMatchResult.getFormulaMatches()).containsExactly(
			h2OMatch,
			h2ODecayMatch
		);
		assertThat(transitionMatchResult.getMatchedInputs().fungibles()).containsExactly(
			inputHydrogen,
			inputOxygen,
			inputH2O
		);
		assertThat(transitionMatchResult.getSatisfiedOutputs().fungibles()).containsExactly(
			outputH2OComposed,
			outputH2ODecayed
		);
	}

	/*
	@Test
	public void testMatchFormulasNoInputs() {
		List<FungibleFormula> possibleFormulas = Arrays.asList(
			FungibleFormula.from(Stream.of(
				new FungibleTransitionMember<>(Oxygen.class, (from, to, meta) -> Result.error("no")),
				new FungibleTransitionMember<>(Hydrogen.class, (from, to, meta) -> Result.error("no"))
				),
				COMPOSITION_H2O)
		);
		Fungible output = new Fungible(mock(H2O.class), H2O.class, UInt256.ONE, 2);
		FungibleInputs fungibleInputs = FungibleInputs.of();
		AtomMetadata metadata = mock(AtomMetadata.class);

		List<FungibleFormulaMatch> matches = GreedyFungibleMatcher.matchFormulas(possibleFormulas, fungibleInputs, output, metadata).stream()
			.filter(FungibleFormulaMatchResult::hasMatch)
			.map(FungibleFormulaMatchResult::getMatch)
			.collect(Collectors.toList());
		assertTrue(matches.isEmpty());
	}
	*/

	/*
	@Test
	public void testMatchFormulasNoApplicableMatch() {
		List<FungibleFormula> possibleFormulas = Arrays.asList(
			FungibleFormula.from(Stream.of(
				new FungibleTransitionMember<>(Oxygen.class, (from, to, meta) -> Result.error("no")),
				new FungibleTransitionMember<>(Hydrogen.class, (from, to, meta) -> Result.error("no"))
				),
				COMPOSITION_H2O)
		);
		Fungible output = new Fungible(mock(H2O.class), H2O.class, UInt256.ONE, 2);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(mock(Oxygen.class), Oxygen.class, UInt256.ONE, 0),
			new Fungible(mock(Hydrogen.class), Hydrogen.class, UInt256.TWO, 1)
		));
		AtomMetadata metadata = mock(AtomMetadata.class);

		List<FungibleFormulaMatch> matches = GreedyFungibleMatcher.matchFormulas(possibleFormulas, fungibleInputs, output, metadata).stream()
			.filter(FungibleFormulaMatchResult::hasMatch)
			.map(FungibleFormulaMatchResult::getMatch)
			.collect(Collectors.toList());
		assertTrue(matches.isEmpty());
	}
	*/

	/*
	@Test
	public void testMatchFormulasPartialMatch() {
		Oxygen applicableOxygen = mock(Oxygen.class);
		Hydrogen applicableHydrogen = mock(Hydrogen.class);
		List<Particle> applicableFungibles = Arrays.asList(
			applicableOxygen,
			applicableHydrogen
		);
		FungibleFormula h20Formula = FungibleFormula.from(Stream.of(
			new FungibleTransitionMember<>(Oxygen.class, (from, to, meta)
				-> Result.of(applicableFungibles.contains(from), () -> "not nice")),
			new FungibleTransitionMember<>(Hydrogen.class, (from, to, meta)
				-> Result.of(applicableFungibles.contains(from), () -> "not nice"))
			),
			COMPOSITION_H2O);
		List<FungibleFormula> possibleFormulas = Arrays.asList(h20Formula);
		Fungible output = new Fungible(mock(H2O.class), H2O.class, UInt256.TWO, 5);
		Fungible oxygenFungible = new Fungible(applicableOxygen, Oxygen.class, UInt256.ONE, 2);
		Fungible hydrogenFungible = new Fungible(applicableHydrogen, Hydrogen.class, UInt256.TWO, 3);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			new Fungible(mock(Oxygen.class), Oxygen.class, UInt256.TWO, 0),
			new Fungible(mock(Hydrogen.class), Hydrogen.class, UInt256.FOUR, 1),
			oxygenFungible,
			hydrogenFungible
		));
		AtomMetadata metadata = mock(AtomMetadata.class);

		List<FungibleFormulaMatch> matches = GreedyFungibleMatcher.matchFormulas(possibleFormulas, fungibleInputs, output, metadata).stream()
			.filter(FungibleFormulaMatchResult::hasMatch)
			.map(FungibleFormulaMatchResult::getMatch)
			.collect(Collectors.toList());
		assertEquals(1, matches.size());
		FungibleFormulaMatch match = matches.get(0);
		assertEquals(1, match.getSatisfiedOutputs().fungibles().count());
		assertEquals(output.withAmount(UInt256.ONE), match.getSatisfiedOutputs().fungibles().findFirst().get());
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(oxygenFungible, hydrogenFungible);
	}
	*/

	/*
	@Test
	public void testMatchFormulasExactMatch() {
		Oxygen applicableOxygen = mock(Oxygen.class);
		Hydrogen applicableHydrogen = mock(Hydrogen.class);
		FungibleFormula h20Formula = FungibleFormula.from(Stream.of(
			new FungibleTransitionMember<>(Oxygen.class, (from, to, meta) -> Result.success()),
			new FungibleTransitionMember<>(Hydrogen.class, (from, to, meta) -> Result.success())),
			COMPOSITION_H2O);
		List<FungibleFormula> possibleFormulas = Arrays.asList(h20Formula);
		Fungible output = new Fungible(mock(H2O.class), H2O.class, UInt256.TWO, 5);
		Fungible oxygenFungible = new Fungible(applicableOxygen, Oxygen.class, UInt256.ONE, 2);
		Fungible hydrogenFungible = new Fungible(applicableHydrogen, Hydrogen.class, UInt256.TWO, 3);
		FungibleInputs fungibleInputs = FungibleInputs.of(Stream.of(
			oxygenFungible,
			hydrogenFungible
		));
		AtomMetadata metadata = mock(AtomMetadata.class);

		List<FungibleFormulaMatch> matches = GreedyFungibleMatcher.matchFormulas(possibleFormulas, fungibleInputs, output, metadata).stream()
			.filter(FungibleFormulaMatchResult::hasMatch)
			.map(FungibleFormulaMatchResult::getMatch)
			.collect(Collectors.toList());
		assertEquals(1, matches.size());
		FungibleFormulaMatch match = matches.get(0);
		assertEquals(1, match.getSatisfiedOutputs().fungibles().count());
		assertEquals(output.withAmount(UInt256.ONE), match.getSatisfiedOutputs().fungibles().findFirst().get());
		assertThat(match.getMatchedInputs().fungibles()).containsExactly(oxygenFungible, hydrogenFungible);
	}
	*/

	@Test
	public void testMatchInitialNoInitial() {
		FungibleTransition transition = mock(FungibleTransition.class);
		when(transition.hasInitial()).thenReturn(false);

		assertEquals(GreedyFungibleMatcher.FungibleTransitionInitialMatchResult.EMPTY,
			GreedyFungibleMatcher.matchInitial(transition, mock(FungibleOutputs.class), mock(AtomMetadata.class)));
	}

	private abstract static class Oxygen extends Particle {
	}

	private abstract static class Hydrogen extends Particle {
	}

	private abstract static class Uranium extends Particle {
	}

	private abstract static class Helium extends Particle {
	}

	private abstract static class H2O extends Particle {
	}
}