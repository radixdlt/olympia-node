package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atomos.FungibleTransition;

import java.util.List;
import java.util.Objects;

// @PackageLocalForTest
final class FungibleTransitionMatch {
	private final FungibleTransition transition;
	private final ImmutableList<FungibleFormulaMatch> machedFormulas;
	private final FungibleOutputs satisfiedFungibleOutputs;
	private final FungibleInputs matchedInputs;
	private final FungibleOutputs matchedInitials;

	FungibleTransitionMatch(FungibleTransition transition, List<FungibleFormulaMatch> matchedFormulas, FungibleOutputs matchedInitials) {
		Objects.requireNonNull(matchedFormulas, "matchedFormulas is required");

		this.transition = Objects.requireNonNull(transition, "transition is required");
		this.matchedInitials = Objects.requireNonNull(matchedInitials);
		this.machedFormulas = ImmutableList.copyOf(matchedFormulas);
		this.satisfiedFungibleOutputs = FungibleOutputs.of(matchedInitials, FungibleOutputs.of(this.machedFormulas.stream()
			.map(FungibleFormulaMatch::getSatisfiedOutputs)
			.flatMap(FungibleOutputs::fungibles)));
		this.matchedInputs = FungibleInputs.of(this.machedFormulas.stream()
			.map(FungibleFormulaMatch::getMatchedInputs)
			.flatMap(FungibleInputs::fungibles));
	}

	FungibleInputs consume(FungibleInputs inputs) {
		return FungibleOutputs.of(this.matchedInputs.fungibles()).consume(inputs);
	}

	boolean hasMatch() {
		return !this.matchedInitials.isEmpty() || !this.machedFormulas.isEmpty();
	}

	FungibleInputs getMatchedInputs() {
		return matchedInputs;
	}

	FungibleOutputs getSatisfiedFungibleOutputs() {
		return satisfiedFungibleOutputs;
	}

	ImmutableList<FungibleFormulaMatch> getMachedFormulas() {
		return this.machedFormulas;
	}

	FungibleOutputs getMatchedInitials() {
		return matchedInitials;
	}

	FungibleTransition getTransition() {
		return this.transition;
	}
}
