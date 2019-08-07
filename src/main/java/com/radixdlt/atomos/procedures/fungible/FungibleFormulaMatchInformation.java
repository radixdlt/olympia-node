package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atomos.procedures.fungible.GreedyFungibleMatcher.FungibleFormulaInputOutputVerdict;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Information about a fungible formula match or mismatch for better error information
 */
class FungibleFormulaMatchInformation {
	private final List<FungibleFormulaInputOutputVerdict> verdictsWithRejections;

	FungibleFormulaMatchInformation(Map<Fungible, FungibleFormulaInputOutputVerdict> inputOutputVerdicts) {
		this.verdictsWithRejections = inputOutputVerdicts.values().stream()
			.filter(verdict -> verdict.getRejectedClass() != null)
			.collect(Collectors.toList());
	}

	Stream<FungibleFormulaInputOutputVerdict> verdictsWithRejections() {
		return verdictsWithRejections.stream();
	}

	boolean hasRejections() {
		return !verdictsWithRejections.isEmpty();
	}
}
