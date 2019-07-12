package com.radixdlt.atomos.procedures.fungible;

/**
 * A result of a match of a formula against a consumer
 */
class FungibleFormulaMatchResult {
	private final FungibleFormulaMatch match;
	private final FungibleFormulaMatchInformation matchInformation;

	FungibleFormulaMatchResult(FungibleFormulaMatch match, FungibleFormulaMatchInformation matchInformation) {
		this.match = match;
		this.matchInformation = matchInformation;
	}

	FungibleFormulaMatch getMatch() {
		return match;
	}

	FungibleFormulaMatchInformation getMatchInformation() {
		return matchInformation;
	}

	boolean hasMatch() {
		return !match.isEmpty();
	}
}
