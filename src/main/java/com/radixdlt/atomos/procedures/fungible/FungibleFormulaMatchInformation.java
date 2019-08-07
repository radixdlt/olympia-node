package com.radixdlt.atomos.procedures.fungible;

import com.radixdlt.atoms.Particle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Information about a fungible formula match or mismatch for better error information
 */
class FungibleFormulaMatchInformation {
	/**
	 * The verdict of an input and an output
	 */
	public static class FungibleFormulaInputOutputVerdict {
		private final Fungible input;
		private final Fungible output;
		private final Class<? extends Particle> approvedClass;
		private final Class<? extends Particle> rejectedClass;

		public FungibleFormulaInputOutputVerdict(
			Fungible input,
			Fungible output,
			Class<? extends Particle> approvedClass,
			Class<? extends Particle> rejectedClass
		) {
			this.input = input;
			this.output = output;
			this.approvedClass = approvedClass;
			this.rejectedClass = rejectedClass;
		}

		public Class<? extends Particle> getApprovedClass() {
			return approvedClass;
		}

		public Class<? extends Particle> getRejectedClass() {
			return rejectedClass;
		}

		public Fungible getOutput() {
			return output;
		}

		public Fungible getInput() {
			return input;
		}
	}

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
