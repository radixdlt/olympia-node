package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.procedures.fungible.Fungible;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import com.radixdlt.utils.UInt256;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Formula defining a fungible transition *from* a set of types *to* a target type with a certain composition
 */
public final class FungibleFormula {
	// TODO could add additional constraint on output here, similar to inputs
	private final Map<Class<? extends Particle>, FungibleTransitionMember<? extends Particle>> inputsByParticleClass;
	private final FungibleComposition composition;

	private FungibleFormula(Map<Class<? extends Particle>, FungibleTransitionMember<? extends Particle>> inputsByParticleClass,
	                        FungibleComposition composition) {
		this.inputsByParticleClass = ImmutableMap.copyOf(inputsByParticleClass);
		this.composition = composition;
	}

	/**
	 * The composition of this formula
	 */
	public FungibleComposition getComposition() {
		return composition;
	}

	/**
	 * Get the inputs to this formula
	 */
	public Map<Class<? extends Particle>, FungibleTransitionMember<? extends Particle>> getInputsByParticleClass() {
		return inputsByParticleClass;
	}

	@Override
	public String toString() {
		return String.format("FixedFormula[%s]", this.composition);
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

		Class<? extends Particle> inputClass = input.getParticleClass();
		while (!inputClass.equals(Particle.class)) {
			for (Map.Entry<Class<? extends Particle>, FungibleTransitionMember<? extends Particle>> fromEntry : inputsByParticleClass.entrySet()) {
				if (fromEntry.getKey().isAssignableFrom(inputClass)) {
					Result checkResult = fromEntry.getValue().check(
						input.getParticle(), output.getParticle(), metadata);

					if (checkResult.isSuccess()) {
						approvedClasses.add(inputClass);
					} else {
						rejectedClasses.put(inputClass, checkResult.getErrorMessage().orElse(""));
					}
				}
			}

			inputClass = (Class<? extends Particle>) inputClass.getSuperclass();
		}

		return new FungibleFormulaInputOutputVerdict(input, output, approvedClasses, rejectedClasses);
	}

	/**
	 * Create a {@link FungibleFormula} from a map of required types and amounts for the transition
	 * @param composition The required amounts by "from" type
	 * @return A fungible transition formulas
	 */
	public static FungibleFormula from(
		Stream<FungibleTransitionMember<? extends Particle>> inputs, FungibleComposition composition) {
		Objects.requireNonNull(composition, "composition is required");

		Map<Class<? extends Particle>, FungibleTransitionMember<? extends Particle>> inputsByClass = new HashMap<>();
		for (FungibleTransitionMember<? extends Particle> input : inputs.collect(Collectors.toList())) {
			if (inputsByClass.containsKey(input.particleClass())) {
				throw new IllegalArgumentException("Input for " + input.particleClass() + " is already defined for formula");
			}

			inputsByClass.put(input.particleClass(), input);
		}

		ImmutableMap<Class<? extends Particle>, UInt256> requiredAmountsPerUnit = composition.requiredAmountsPerUnit();
		requiredAmountsPerUnit.forEach((particleClass, amount) -> {
				Objects.requireNonNull(amount, "amount is required");
				if (amount.isZero()) {
					throw new IllegalArgumentException("Amount must be > 0, amount for " + particleClass + " is " + amount);
				}
			}
		);

		if (inputsByClass.keySet().stream().anyMatch(fromParticleClass -> !requiredAmountsPerUnit.containsKey(fromParticleClass))
			|| requiredAmountsPerUnit.keySet().stream().anyMatch(requiredType -> !inputsByClass.containsKey(requiredType))) {
			throw new IllegalArgumentException("Types used by transition formulas and declared as 'froms' do not match: "
				+ inputsByClass.keySet() + " should equal " + requiredAmountsPerUnit.keySet());
		}

		return new FungibleFormula(inputsByClass, composition);
	}

	/**
	 * The verdict of an input and an output
	 */
	public static class FungibleFormulaInputOutputVerdict {
		private final Fungible input;
		private final Fungible output;
		private final List<Class<? extends Particle>> approvedClasses;
		private final Map<Class<? extends Particle>, String> rejectedClasses;

		private FungibleFormulaInputOutputVerdict(Fungible input, Fungible output, List<Class<? extends Particle>> approvedClasses, Map<Class<? extends Particle>, String> rejectedClasses) {
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
