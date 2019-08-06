package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.atomos.Result;
import com.radixdlt.atomos.procedures.fungible.FungibleMatcher.FungibleMatcherResult;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of check for verifying all transitions between input and output particles
 */
public final class FungibleTransitionConstraintCheck {
	private final List<FungibleTransition<?>> fungibleTransitions;
	private final ParticleValueMapper valueMapper;
	private final FungibleMatcher matcher = new GreedyFungibleMatcher();

	public FungibleTransitionConstraintCheck(List<FungibleTransition<? extends Particle>> fungibleTransitions) {
		Objects.requireNonNull(fungibleTransitions, "fungibleTransitions is required");

		this.fungibleTransitions = ImmutableList.copyOf(fungibleTransitions);
		this.valueMapper = ParticleValueMapper.from(fungibleTransitions);
	}

	/**
	 * Validate all {@link FungibleTransition}s between given inputs and outputs
	 *  @param inputParticles The input fungibles
	 * @param outputParticles   The output particles
	 * @param metadata            The metadata of that atom
	 */
	public Result validateParticles(List<Particle> inputParticles, List<Particle> outputParticles, AtomMetadata metadata) {
		return this.validate(
			toFungibles(inputParticles.stream(), this.valueMapper, outputParticles.size()),
			toFungibles(outputParticles.stream(), this.valueMapper),
			metadata).getResult();
	}

	public static class FungibleValidationResult {
		private final Result result;
		private final FungibleMatcherResult matchResult;

		private FungibleValidationResult(Result result, FungibleMatcherResult matchResult) {
			this.result = result;
			this.matchResult = matchResult;
		}

		public Result getResult() {
			return result;
		}

		public List<FungibleTransitionMatchResult> getMatchResults() {
			return matchResult.getMatchResults();
		}
	}

	/**
	 * Validate all {@link FungibleTransition}s between given inputs and outputs
	 *  @param inputFungibles  The input fungibles
	 * @param outputFungibles The output fungibles
	 * @param metadata        The metadata of that atom
	 */
	public FungibleValidationResult validate(Stream<Fungible> inputFungibles, Stream<Fungible> outputFungibles, AtomMetadata metadata) {
		FungibleOutputs fungibleOutputs = FungibleOutputs.of(outputFungibles);
		FungibleInputs fungibleInputs = FungibleInputs.of(inputFungibles);
		FungibleOutputs satisfiedFungibleOutputs = FungibleOutputs.of();
		FungibleInputs unspentFungibleInputs = fungibleInputs;

		FungibleMatcherResult matchResult = matcher.match(this.fungibleTransitions, fungibleOutputs, unspentFungibleInputs, metadata);
		for (FungibleTransitionMatch match : matchResult.getMatches()) {
			satisfiedFungibleOutputs = FungibleOutputs.of(match.getSatisfiedFungibleOutputs(), satisfiedFungibleOutputs);
			unspentFungibleInputs = match.consume(unspentFungibleInputs);
		}

		FungibleOutputs unsatisfiedFungibleOutputs = FungibleOutputs.diff(fungibleOutputs, satisfiedFungibleOutputs);
		FungibleInputs spentFungibleInputs = FungibleInputs.diff(fungibleInputs, unspentFungibleInputs);

		Result result = checkAllRequiresSatisfied(this.fungibleTransitions, unsatisfiedFungibleOutputs, satisfiedFungibleOutputs,
			unspentFungibleInputs, spentFungibleInputs, matchResult.getMatchResults(), valueMapper);

		return new FungibleValidationResult(result, matchResult);
	}

	/**
	 * Report an error if not all inputs have been spent or if not all outputs have been satisfied
	 */
	// @PackageLocalForTest
	static Result checkAllRequiresSatisfied(List<FungibleTransition<?>> fungibleTransitions,
	                                        FungibleOutputs unsatisfiedFungibleOutputs,
	                                        FungibleOutputs satisfiedFungibleOutputs,
	                                        FungibleInputs unspentFungibleInputs,
	                                        FungibleInputs spentFungibleInputs,
	                                        List<FungibleTransitionMatchResult> matchResults,
	                                        ParticleValueMapper valueMapper) {
		if (unsatisfiedFungibleOutputs.isEmpty() && unspentFungibleInputs.isEmpty()) {
			return Result.success();
		}

		String fungibleTransitionString = transitionsToString(
			fungibleTransitions, unsatisfiedFungibleOutputs, satisfiedFungibleOutputs,
			unspentFungibleInputs, spentFungibleInputs, matchResults, valueMapper);

		return Result.error(String.format(
			"Fungible transition requirements violated, outputs do not equal to inputs: %s",
			fungibleTransitionString));
	}

	/**
	 * Utility method to convert a list of transitions and inputs / outputs info to a well formatted string
	 */
	private static String transitionsToString(
		List<FungibleTransition<?>> fungibleTransitions,
		FungibleOutputs unsatisfiedFungibleOutputs,
		FungibleOutputs satisfiedFungibleOutputs,
		FungibleInputs unspentFungibleInputs,
		FungibleInputs spentFungibleInputs,
		List<FungibleTransitionMatchResult> matchResults,
		ParticleValueMapper valueMapper
	) {
		UnaryOperator<String> noneIfEmpty = s -> s.isEmpty() ? "\t<none>" : s;

		String unspentInputsString = noneIfEmpty.apply(unspentFungibleInputs.fungibles()
			.map(unspentInput -> String.format("\t-> %s unspent of %s %s from %s",
				unspentFungibleInputs.amount(unspentInput.getParticle()), valueMapper.amount(unspentInput.getParticle()),
				unspentInput.getParticleClass().getSimpleName(), unspentInput.identifierString()))
			.collect(Collectors.joining(System.lineSeparator())));
		String spentInputsString = noneIfEmpty.apply(spentFungibleInputs.fungibles()
			.filter(fungible -> !unspentFungibleInputs.contains(fungible.getParticle()))
			.map(spentInput -> String.format("\t-> %s %s spent from %s",
				valueMapper.amount(spentInput.getParticle()), spentInput.getParticleClass().getSimpleName(),
				spentInput.identifierString()))
			.collect(Collectors.joining(System.lineSeparator())));
		String unsatisfiedOutputsString = noneIfEmpty.apply(unsatisfiedFungibleOutputs.fungibles()
			.map(unsatisfiedOutput -> String.format("\t-> %s unsatisfied of %s %s for %s",
				unsatisfiedOutput.getAmount(), valueMapper.amount(unsatisfiedOutput.getParticle()),
				unsatisfiedOutput.getParticleClass().getSimpleName(), unsatisfiedOutput.identifierString()))
			.collect(Collectors.joining(System.lineSeparator())));
		String satisfiedOutputsString = noneIfEmpty.apply(satisfiedFungibleOutputs.fungibles()
			.filter(fungible -> !unsatisfiedFungibleOutputs.contains(fungible.getParticle()))
			.map(satisfiedOutput -> String.format("\t-> %s %s satisfied for %s",
				satisfiedOutput.getAmount(), satisfiedOutput.getParticleClass().getSimpleName(),
				satisfiedOutput.identifierString()))
			.collect(Collectors.joining(System.lineSeparator())));
		String transitionNamesString = noneIfEmpty.apply(fungibleTransitions.stream()
			.map(FungibleTransition::getOutputParticleClass)
			.map(Class::getName)
			.collect(Collectors.joining(", ")));
		String matchesString = noneIfEmpty.apply(matchesToString(matchResults));
		String rejectedString = noneIfEmpty.apply(matchRejectionsToString(matchResults));

		return String.format(
			"transitions to %s.%n Unspent Inputs%n%s%n Spent Inputs%n%s%n Unsatisfied Outputs%n%s%n"
				+ " Satisfied Outputs%n%s%n Matched%n%s%n Rejected%n%s",
			transitionNamesString, unspentInputsString, spentInputsString, unsatisfiedOutputsString,
			satisfiedOutputsString, matchesString, rejectedString);
	}

	/**
	 * Utility method to convert a list of match results to a single string containing all matches
	 */
	private static String matchesToString(List<FungibleTransitionMatchResult> matchResults) {
		Function<Fungible, String> fungibleToStringFunction = fungible -> String.format("%s %s[%d]",
			fungible.getAmount(), fungible.getParticleClass().getSimpleName(), fungible.getIndex());

		return matchResults.stream()
			.filter(FungibleTransitionMatchResult::hasMatch)
			.map(matchResult -> String.format("\t Transition to %s%n%s%s",
				matchResult.getTransition().getOutputParticleClass().getSimpleName(),
				matchResult.getMatch().getMatchedInitials().isEmpty() ? ""
					: (matchResult.getMatch().getMatchedInitials().fungibles()
						.map(fungibleToStringFunction)
						.map(initial -> String.format("\t\t <initial> => %s", initial))
						.collect(Collectors.joining(System.lineSeparator())) + System.lineSeparator()),
				matchResult.getFormulaMatches().stream().map(formulaMatch ->
					String.format("\t\t -> %s => %s%n\t\t\t with %s",
						formulaMatch.getMatchedInputs().fungibles()
							.map(fungibleToStringFunction)
							.collect(Collectors.joining(" + ")),
						formulaMatch.getSatisfiedOutputs().fungibles()
							.map(fungibleToStringFunction)
							.collect(Collectors.joining(" + ")),
						formulaMatch.getFormula()))
					.collect(Collectors.joining(System.lineSeparator()))))
			.collect(Collectors.joining(System.lineSeparator()));
	}

	/**
	 * Utility method to convert a list of match results to a single string containing extra information (e.g. rejects)
	 */
	private static String matchRejectionsToString(List<FungibleTransitionMatchResult> matchResults) {
		Function<Fungible, String> fungibleToStringFunction = fungible -> String.format("%s %s[%d]",
			fungible.getAmount(), fungible.getParticleClass().getSimpleName(), fungible.getIndex());

		return matchResults.stream()
			.filter(matchResult -> matchResult.getInformation().hasRejections())
			.map(matchResult -> String.format("\t In Transition to %s%n%s%s",
				matchResult.getTransition().getOutputParticleClass().getSimpleName(),
				matchResult.getInformation().hasInitialVerdictsWithRejections()
					? String.format("\t\t initials: %n%s%n", matchResult.getInformation().initialVerdictsWithRejections()
						.map(verdict -> String.format("\t\t\t rejected %s: '%s'",
							verdict.getOutput(), verdict.getRejectionMessage()))
						.collect(Collectors.joining(System.lineSeparator())))
					: "",
				matchResult.getInformation().getMatchInformation().stream()
					.filter(FungibleFormulaMatchInformation::hasRejections)
					.map(matchInformation ->
						String.format("\t\t In %s%n%s",
							matchInformation.getFormula(),
							matchInformation.verdictsWithRejections()
								.map(verdict ->
									String.format("\t\t\t %s => %s: %n%s",
										fungibleToStringFunction.apply(verdict.getInput()),
										fungibleToStringFunction.apply(verdict.getOutput()),
										verdict.getRejectedClasses().entrySet().stream()
											.map(reject -> String.format("\t\t\t\t rejected by %s: '%s'",
												reject.getKey().getSimpleName(), reject.getValue()))
											.collect(Collectors.joining(System.lineSeparator()))))
								.collect(Collectors.joining(System.lineSeparator()))
						))
					.collect(Collectors.joining(System.lineSeparator()))))
			.collect(Collectors.joining(System.lineSeparator()));
	}

	/**
	 * Convert particles to indexed fungibles in order with no index offset
	 */
	private static Stream<Fungible> toFungibles(Stream<Particle> particles, ParticleValueMapper valueMapper) {
		return toFungibles(particles, valueMapper, 0);
	}

	/**
	 * Convert particles to indexed fungibles in order with an index offset
	 */
	private static Stream<Fungible> toFungibles(Stream<Particle> particles, ParticleValueMapper valueMapper, int indexOffset) {
		return Streams.mapWithIndex(particles,
			(p, i) -> new Fungible(p, valueMapper.amount(p), (int) i + indexOffset));
	}
}