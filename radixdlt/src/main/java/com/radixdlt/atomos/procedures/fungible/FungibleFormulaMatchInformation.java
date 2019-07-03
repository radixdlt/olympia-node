package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atomos.FungibleFormula;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Information about a fungible formula match or mismatch for better error information
 */
class FungibleFormulaMatchInformation {
	private final FungibleFormula formula;
	private final List<FungibleFormula.FungibleFormulaInputOutputVerdict> verdictsWithRejections;

	FungibleFormulaMatchInformation(FungibleFormula formula, Map<Fungible, FungibleFormula.FungibleFormulaInputOutputVerdict> inputOutputVerdicts) {
		this.formula = formula;
		this.verdictsWithRejections = inputOutputVerdicts.values().stream()
			.filter(verdict -> !verdict.getRejectedClasses().isEmpty())
			.collect(Collectors.toList());
	}

	FungibleFormula getFormula() {
		return formula;
	}

	Stream<FungibleFormula.FungibleFormulaInputOutputVerdict> verdictsWithRejections() {
		return verdictsWithRejections.stream();
	}

	boolean hasRejections() {
		return !verdictsWithRejections.isEmpty();
	}
}
