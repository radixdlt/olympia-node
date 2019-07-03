package com.radixdlt.atomos.procedures.fungible;

import java.util.Objects;
import java.util.stream.Stream;

// @PackageLocalForTest
final class InputsOutputsMatch {
	static final InputsOutputsMatch EMPTY =
		new InputsOutputsMatch(FungibleOutputs.of(Stream.empty()), FungibleInputs.of(Stream.empty()));

	private final FungibleOutputs satisfiedOutputs;
	private final FungibleInputs matchedInputs;

	InputsOutputsMatch(Fungible satisfiedConsumer, FungibleInputs matchedInputs) {
		this.satisfiedOutputs = FungibleOutputs.of(Stream.of(satisfiedConsumer));
		this.matchedInputs = Objects.requireNonNull(matchedInputs);
	}

	InputsOutputsMatch(FungibleOutputs satisfiedOutputs, FungibleInputs matchedInputs) {
		this.satisfiedOutputs = Objects.requireNonNull(satisfiedOutputs);
		this.matchedInputs = Objects.requireNonNull(matchedInputs);
	}

	FungibleOutputs getSatisfiedOutputs() {
		return satisfiedOutputs;
	}

	/**
	 * Get the matchedInputs consumables for this match
	 * @return The matchedInputs consumables
	 */
	FungibleInputs getMatchedInputs() {
		return matchedInputs;
	}

	/**
	 * Consume the given inputs with the matched inputs
	 */
	FungibleInputs consume(FungibleInputs inputs) {
		return FungibleOutputs.of(matchedInputs.fungibles()).consume(inputs);
	}
}
