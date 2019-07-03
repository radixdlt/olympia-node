package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atomos.FungibleTransition;
import com.radixdlt.constraintmachine.AtomMetadata;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Matcher for matching inputs and outputs using a set of transitions
 */
public interface FungibleMatcher {
	/**
	 * Match fungibleInputs and fungibleOutputs with a set of transitions
	 * @param transitions The fungible transitions
	 * @param fungibleOutputs The fungibleOutputs
	 * @param fungibleInputs The fungibleInputs
	 * @param metadata Atom metadata of the containing atom
	 * @return A list of matches of individual transitions against selected fungibleOutputs and fungibleInputs
	 */
	FungibleMatcherResult match(List<FungibleTransition<?>> transitions, FungibleOutputs fungibleOutputs, FungibleInputs fungibleInputs, AtomMetadata metadata);

	/**
	 * The result of a match against a set of transitions with given outputs and inputs
	 * Contains the matches and extra information about matches and mismatches
	 */
	class FungibleMatcherResult {
		private final List<FungibleTransitionMatchResult> matchResults;
		private final List<FungibleTransitionMatch> matches;

		FungibleMatcherResult(List<FungibleTransitionMatchResult> matchResults) {
			this.matchResults = Objects.requireNonNull(matchResults);
			this.matches = this.matchResults.stream()
				.map(FungibleTransitionMatchResult::getMatch)
				.collect(Collectors.toList());
		}

		List<FungibleTransitionMatchResult> getMatchResults() {
			return this.matchResults;
		}

		List<FungibleTransitionMatch> getMatches() {
			return this.matches;
		}
	}
}
