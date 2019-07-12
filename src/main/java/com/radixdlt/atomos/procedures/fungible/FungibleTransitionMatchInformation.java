package com.radixdlt.atomos.procedures.fungible;

import com.google.common.collect.ImmutableList;
import com.radixdlt.atomos.FungibleTransition.FungibleTransitionInitialVerdict;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Information about a fungible transition match or mismatch for better error information
 */
class FungibleTransitionMatchInformation {
	private final List<FungibleFormulaMatchInformation> matchInformation;
	private final List<FungibleTransitionInitialVerdict> initialVerdictsWithRejections;
	private final boolean hasRejections;

	FungibleTransitionMatchInformation(List<FungibleFormulaMatchInformation> matchInformation, List<FungibleTransitionInitialVerdict> initialVerdicts) {
		Objects.requireNonNull(matchInformation, "matchInformation is required");
		Objects.requireNonNull(initialVerdicts, "initialVerdicts is required");

		this.matchInformation = ImmutableList.copyOf(matchInformation);
		this.initialVerdictsWithRejections = initialVerdicts.stream()
			.filter(FungibleTransitionInitialVerdict::isRejection)
			.collect(Collectors.toList());
		this.hasRejections = !this.initialVerdictsWithRejections.isEmpty()
			|| this.matchInformation.stream().anyMatch(FungibleFormulaMatchInformation::hasRejections);
	}

	List<FungibleFormulaMatchInformation> getMatchInformation() {
		return matchInformation;
	}

	Stream<FungibleTransitionInitialVerdict> initialVerdictsWithRejections() {
		return this.initialVerdictsWithRejections.stream();
	}

	boolean hasInitialVerdictsWithRejections() {
		return !this.initialVerdictsWithRejections.isEmpty();
	}

	boolean hasRejections() {
		return this.hasRejections;
	}
}
