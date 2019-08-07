package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atomos.FungibleFormula;
import java.util.Objects;

/**
 * A match of a {@link FungibleFormula} to get a certain consuming amount by consuming certain amounts of certain fungibles
 */
// @PackageLocalForTest
final class FungibleFormulaMatch {
	private final InputsOutputsMatch match;

	FungibleFormulaMatch(InputsOutputsMatch match) {
		this.match = Objects.requireNonNull(match, "match is required");
	}

	FungibleOutputs getSatisfiedOutputs() {
		return this.match.getSatisfiedOutputs();
	}

	FungibleInputs getMatchedInputs() {
		return this.match.getMatchedInputs();
	}

	FungibleInputs consume(FungibleInputs inputs) {
		return match.consume(inputs);
	}

	boolean isEmpty() {
		return this.match == InputsOutputsMatch.EMPTY;
	}

	/**
	 * Materialize an empty fungible formula match of the given formula
	 */
	static FungibleFormulaMatch empty() {
		return new FungibleFormulaMatch(InputsOutputsMatch.EMPTY);
	}
}
