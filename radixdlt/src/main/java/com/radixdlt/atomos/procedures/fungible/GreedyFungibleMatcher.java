package com.radixdlt.atomos.procedures.fungible;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.radixdlt.atomos.FungibleFormula;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.FungibleTransition.FungibleTransitionInitialVerdict;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;

/**
 * A greedy implementation of a {@link FungibleMatcher},
 * yielding the first match without attempting to find an optimal match.
 */
class GreedyFungibleMatcher implements FungibleMatcher {
	private final FungibleTransitionMatcher transitionMatcher;
	private final FungibleFormulasMatcher formulaMatcher;

	GreedyFungibleMatcher() {
		this(GreedyFungibleMatcher::matchTransition, GreedyFungibleMatcher::matchFormulas);
	}

	GreedyFungibleMatcher(FungibleTransitionMatcher transitionMatcher, FungibleFormulasMatcher formulaMatcher) {
		this.transitionMatcher = Objects.requireNonNull(transitionMatcher);
		this.formulaMatcher = Objects.requireNonNull(formulaMatcher);
	}

	@Override
	public FungibleMatcherResult match(List<FungibleTransition<?>> transitions, FungibleOutputs fungibleOutputs, FungibleInputs fungibleInputs, AtomMetadata metadata) {
		Objects.requireNonNull(transitions, "transitions is required");
		Objects.requireNonNull(fungibleOutputs, "fungibleOutputs is required");
		Objects.requireNonNull(fungibleInputs, "fungibleInputs is required");
		Objects.requireNonNull(metadata, "metadata is required");

		List<FungibleTransitionMatchResult> matchResults = new ArrayList<>();

		for (FungibleTransition<?> transition : transitions) {
			final FungibleOutputs outputsForTransition = getOutputsForTransition(fungibleOutputs, transition);
			if (outputsForTransition.isEmpty()) {
				continue; // no need to check further if there is nothing to match
			}

			FungibleTransitionMatchResult matchResult = transitionMatcher.match(transition, fungibleInputs, outputsForTransition, metadata, formulaMatcher);
			fungibleInputs = matchResult.getMatch().consume(fungibleInputs);
			fungibleOutputs = FungibleOutputs.diff(fungibleOutputs, matchResult.getMatch().getSatisfiedFungibleOutputs());
			matchResults.add(matchResult);
		}

		return new FungibleMatcherResult(matchResults);
	}

	private static FungibleOutputs getOutputsForTransition(FungibleOutputs fungibleOutputs, FungibleTransition<?> transition) {
		Map<Class<?>, FungibleOutputs> outputsByClass = fungibleOutputs.group(Fungible::getParticleClass);
		FungibleOutputs fungibleOutputsInTransition = FungibleOutputs.of();

		for (Map.Entry<Class<?>, FungibleOutputs> outputsEntry : outputsByClass.entrySet()) {
			Class<?> outputClass = outputsEntry.getKey();
			FungibleOutputs fungibleOutputsForClass = outputsEntry.getValue();
			boolean belongsToTransition = outputClass.isAssignableFrom(transition.getOutputParticleClass());
			if (!belongsToTransition) {
				while (!outputClass.equals(Particle.class)) {
					outputClass = outputClass.getSuperclass();

					if (transition.getOutputParticleClass().isAssignableFrom(outputClass)) {
						belongsToTransition = true;
						break;
					}
				}
			}

			if (belongsToTransition) {
				fungibleOutputsInTransition = FungibleOutputs.of(fungibleOutputsInTransition, fungibleOutputsForClass);
			}
		}

		return fungibleOutputsInTransition;
	}

	/**
	 * Internal interface for transition matching
	 */
	// @PackageLocalForTest
	interface FungibleTransitionMatcher {
		FungibleTransitionMatchResult match(FungibleTransition<? extends Particle> transition, FungibleInputs fungibleInputs, FungibleOutputs fungibleOutputs,
		                                    AtomMetadata metadata, FungibleFormulasMatcher formulaMatcher);
	}

	/**
	 * Drain a transition with given fungibles
	 * Note: The matching of formulas against the fungibleOutputs and consuming is done greedily, not optimally.
	 * As the order of {@link Particle}s within the {@link ParticleGroup} is preserved,
	 * clients may order their fungibleOutputs and consuming in whichever way they need.
	 *
	 * @param transition The fungible transition
	 * @param metadata   The atom metadata
	 * @return The consumed amount and the unsatisfied fungibleOutputs
	 */
	// @PackageLocalForTest
	static FungibleTransitionMatchResult matchTransition(FungibleTransition<? extends Particle> transition,
	                                                     FungibleInputs fungibleInputs, FungibleOutputs fungibleOutputs,
	                                                     AtomMetadata metadata, FungibleFormulasMatcher formulaMatcher) {
		Objects.requireNonNull(formulaMatcher, "formulaMatcher is required");

		List<FungibleFormulaMatch> matchedFormulas = new ArrayList<>();
		List<FungibleFormulaMatchInformation> matchInformation = new ArrayList<>();

		for (Fungible output : fungibleOutputs.fungibles().collect(Collectors.toList())) {
			List<FungibleFormulaMatchResult> outputMatchResults = formulaMatcher.match(transition.getFormulas(), fungibleInputs, output, metadata);
			for (FungibleFormulaMatchResult outputMatchResult : outputMatchResults) {
				if (outputMatchResult.hasMatch()) {
					matchedFormulas.add(outputMatchResult.getMatch());
					fungibleInputs = outputMatchResult.getMatch().consume(fungibleInputs);
				}

				matchInformation.add(outputMatchResult.getMatchInformation());
			}
		}

		// compute remaining outputs based on matches for initial matching
		fungibleOutputs = FungibleOutputs.diff(fungibleOutputs, FungibleOutputs.of(matchedFormulas.stream()
			.flatMap(f -> f.getSatisfiedOutputs().fungibles())));
		// match initials after all formulas have been matched
		FungibleTransitionInitialMatchResult initialMatch = matchInitial(transition, fungibleOutputs, metadata);

		FungibleTransitionMatch transitionMatch = new FungibleTransitionMatch(transition, matchedFormulas, initialMatch.matchedInitials);
		FungibleTransitionMatchInformation information = new FungibleTransitionMatchInformation(matchInformation, initialMatch.verdicts);
		return new FungibleTransitionMatchResult(transition, transitionMatch, information);
	}

	/**
	 * Match initials of a transition against some outputs
	 */
	// @PackageLocalForTest
	static class FungibleTransitionInitialMatchResult {
		static final FungibleTransitionInitialMatchResult EMPTY =
			new FungibleTransitionInitialMatchResult(FungibleOutputs.of(), Lists.newArrayList());

		private final List<FungibleTransitionInitialVerdict> verdicts;
		private final FungibleOutputs matchedInitials;

		private FungibleTransitionInitialMatchResult(FungibleOutputs matchedInitials, List<FungibleTransitionInitialVerdict> verdicts) {
			this.verdicts = verdicts;
			this.matchedInitials = matchedInitials;
		}

		FungibleOutputs getMatchedInitials() {
			return matchedInitials;
		}
	}

	// @PackageLocalForTest
	static FungibleTransitionInitialMatchResult matchInitial(FungibleTransition transition, FungibleOutputs outputs, AtomMetadata metadata) {
		if (!transition.hasInitial()) {
			return FungibleTransitionInitialMatchResult.EMPTY;
		}

		final List<FungibleTransitionInitialVerdict> verdicts = new ArrayList<>();
		FungibleOutputs matchedInitials = FungibleOutputs.of();

		for (Fungible output : outputs.fungibles().collect(Collectors.toList())) {
			if (!transition.getOutputParticleClass().isInstance(output.getParticle())) {
				continue; // particle is not of type of interest, safeguard against being passed unchecked outputs
			}

			FungibleTransitionInitialVerdict initialVerdict = transition.checkInitial(output, metadata);
			verdicts.add(initialVerdict);
			if (initialVerdict.isApproval()) {
				matchedInitials = FungibleOutputs.of(matchedInitials, output);
			}
		}

		return new FungibleTransitionInitialMatchResult(matchedInitials, verdicts);
	}

	/**
	 * Internal interface for formula matching
	 */
	// @PackageLocalForTest
	interface FungibleFormulasMatcher {
		List<FungibleFormulaMatchResult> match(List<FungibleFormula> possibleFormulas, FungibleInputs fungibleInputs, Fungible output, AtomMetadata metadata);
	}

	/**
	 * Get the applicable formulas from possible formulas given a output amount and a set of fungibleInputs
	 *
	 * @param possibleFormulas The possible formulas
	 * @param fungibleInputs      The remaining fungibles to be used
	 * @param output         The output to match
	 * @param metadata         The atom metadata
	 * @return The formulas that can be used to satisfy the required consuming amount
	 */
	// @PackageLocalForTest
	static List<FungibleFormulaMatchResult> matchFormulas(List<FungibleFormula> possibleFormulas, FungibleInputs fungibleInputs, Fungible output, AtomMetadata metadata) {
		List<FungibleFormulaMatchResult> formulaMatchResults = new ArrayList<>();
		UInt256 remainingConsumer = output.getAmount();

		for (FungibleFormula formula : possibleFormulas) {
			if (fungibleInputs.isEmpty() || remainingConsumer.isZero()) {
				break; // no need to look for more applicable formulas if nothing is left to be matched
			}

			Map<Fungible, FungibleFormula.FungibleFormulaInputOutputVerdict> inputVerdicts = fungibleInputs.fungibles()
				.collect(Collectors.toMap(input -> input, input
					-> formula.getVerdictForInput(input, output, metadata)));
			Map<Fungible, List<Class<? extends Particle>>> approvedClasses = inputVerdicts.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getApprovedClasses()));
			FungibleInputs.CompositionMatch compositionMatch =
				fungibleInputs.match(output.getAmount(), formula.getComposition(), approvedClasses);

			FungibleFormulaMatch formulaMatch;
			if (!compositionMatch.getSatisfiedAmount().isZero()) {
				remainingConsumer = remainingConsumer.subtract(compositionMatch.getSatisfiedAmount());
				fungibleInputs = compositionMatch.consume(fungibleInputs);
				formulaMatch = new FungibleFormulaMatch(formula, compositionMatch.withConsumer(output));
			} else {
				formulaMatch = FungibleFormulaMatch.empty(formula);
			}

			FungibleFormulaMatchInformation matchInformation = new FungibleFormulaMatchInformation(formula, inputVerdicts);
			formulaMatchResults.add(new FungibleFormulaMatchResult(formulaMatch, matchInformation));
		}

		return formulaMatchResults;
	}

}