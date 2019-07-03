package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atomos.FungibleTransition;

import java.util.List;
import java.util.Objects;

/**
 * The result of a match of a transition against inputs and outputs
 */
class FungibleTransitionMatchResult {
	private final FungibleTransition transition;
	private final FungibleTransitionMatch match;
	private final FungibleTransitionMatchInformation information;

	FungibleTransitionMatchResult(FungibleTransition transition, FungibleTransitionMatch match, FungibleTransitionMatchInformation information) {
		this.transition = Objects.requireNonNull(transition, "transition is required");
		this.match = Objects.requireNonNull(match, "match is required");
		this.information = Objects.requireNonNull(information, "information is required");
	}

	FungibleTransition getTransition() {
		return transition;
	}

	FungibleTransitionMatch getMatch() {
		return match;
	}

	boolean hasMatch() {
		return this.match.hasMatch();
	}

	FungibleInputs getMatchedInputs() {
		return this.match.getMatchedInputs();
	}

	FungibleOutputs getSatisfiedOutputs() {
		return this.match.getSatisfiedFungibleOutputs();
	}

	FungibleTransitionMatchInformation getInformation() {
		return this.information;
	}

	List<FungibleFormulaMatch> getFormulaMatches() {
		return this.match.getMachedFormulas();
	}
}
