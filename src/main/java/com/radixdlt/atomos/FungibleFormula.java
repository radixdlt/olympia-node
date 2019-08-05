package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.procedures.fungible.Fungible;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Formula defining a fungible transition *from* a set of types *to* a target type with a certain composition
 */
public final class FungibleFormula {
	// TODO could add additional constraint on output here, similar to inputs
	private final FungibleTransitionMember<? extends Particle> inputTransition;

	private FungibleFormula(FungibleTransitionMember<? extends Particle> inputTransition) {
		this.inputTransition = inputTransition;
	}

	public FungibleTransitionMember<? extends Particle> getInputTransition() {
		return inputTransition;
	}

	/**
	 * Get the set of from classes a certain input particle can satisfy for the given output
	 */
	public FungibleFormulaInputOutputVerdict getVerdictForInput(Fungible input, Fungible output, AtomMetadata metadata) {
		Objects.requireNonNull(input, "input is required");
		Objects.requireNonNull(output, "output is required");
		Objects.requireNonNull(metadata, "metadata is required");

		List<Class<? extends Particle>> approvedClasses = new ArrayList<>();
		Map<Class<? extends Particle>, String> rejectedClasses = new HashMap<>();

		final Class<? extends Particle> inputClass = input.getParticleClass();
		if (inputTransition.particleClass().isAssignableFrom(inputClass)) {
			Result checkResult = inputTransition.check(input.getParticle(), output.getParticle(), metadata);

			if (checkResult.isSuccess()) {
				approvedClasses.add(inputClass);
			} else {
				rejectedClasses.put(inputClass, checkResult.getErrorMessage().orElse(""));
			}
		}

		return new FungibleFormulaInputOutputVerdict(input, output, approvedClasses, rejectedClasses);
	}

	/**
	 * Create a {@link FungibleFormula} from a map of required types and amounts for the transition
	 * @return A fungible transition formulas
	 */
	public static FungibleFormula from(FungibleTransitionMember<? extends Particle> inputTransition) {
		return new FungibleFormula(inputTransition);
	}

	/**
	 * The verdict of an input and an output
	 */
	public static class FungibleFormulaInputOutputVerdict {
		private final Fungible input;
		private final Fungible output;
		private final List<Class<? extends Particle>> approvedClasses;
		private final Map<Class<? extends Particle>, String> rejectedClasses;

		private FungibleFormulaInputOutputVerdict(
			Fungible input,
			Fungible output,
			List<Class<? extends Particle>> approvedClasses,
			Map<Class<? extends Particle>, String> rejectedClasses
		) {
			this.input = input;
			this.output = output;
			this.approvedClasses = ImmutableList.copyOf(approvedClasses);
			this.rejectedClasses = ImmutableMap.copyOf(rejectedClasses);
		}

		public List<Class<? extends Particle>> getApprovedClasses() {
			return approvedClasses;
		}

		public Map<Class<? extends Particle>, String> getRejectedClasses() {
			return rejectedClasses;
		}

		public Fungible getOutput() {
			return output;
		}

		public Fungible getInput() {
			return input;
		}
	}
}
