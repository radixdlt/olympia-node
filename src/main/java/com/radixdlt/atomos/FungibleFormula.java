package com.radixdlt.atomos;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.radixdlt.atomos.AtomOS.FungibleTransitionInputConstraint;
import com.radixdlt.atomos.procedures.fungible.Fungible;
import com.radixdlt.atoms.Particle;
import com.radixdlt.constraintmachine.AtomMetadata;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Formula defining a fungible transition *from* a set of types *to* a target type with a certain composition
 */
public final class FungibleFormula {
	private final Class<? extends Particle> particleClass;
	private final FungibleTransitionInputConstraint<? extends Particle, ? extends Particle> constraint;
	private final BiPredicate<Particle, Particle> transition;

	public FungibleFormula(
		Class<? extends Particle> particleClass,
		FungibleTransitionInputConstraint<? extends Particle, ? extends Particle> constraint,
		BiPredicate<? extends Particle, ? extends Particle> transition
	) {
		this.particleClass = Objects.requireNonNull(particleClass, "particleClass is required");
		this.constraint = Objects.requireNonNull(constraint, "constraint is required");
		this.transition = Objects.requireNonNull((BiPredicate<Particle, Particle>) transition);
	}

	public Class<? extends Particle> particleClass() {
		return this.particleClass;
	}

	/**
	 * Get the set of from classes a certain input particle can satisfy for the given output
	 */
	public FungibleFormulaInputOutputVerdict getVerdictForInput(Fungible input, Fungible output, AtomMetadata metadata) {
		Objects.requireNonNull(input, "input is required");
		Objects.requireNonNull(output, "output is required");
		Objects.requireNonNull(metadata, "metadata is required");

		final Class<? extends Particle> inputClass = input.getParticleClass();
		if (this.particleClass.isAssignableFrom(inputClass)) {
			Result checkResult = ((FungibleTransitionInputConstraint) this.constraint)
				.apply(input.getParticle(), output.getParticle(), metadata);

			boolean transitionCheck = transition.test(input.getParticle(), output.getParticle());

			if (checkResult.isSuccess() && transitionCheck) {
				return new FungibleFormulaInputOutputVerdict(input, output, Collections.singletonList(inputClass), Collections.emptyMap());
			} else {
				return new FungibleFormulaInputOutputVerdict(input, output, Collections.emptyList(),
					Collections.singletonMap(inputClass, checkResult.getErrorMessage().orElse(""))
				);
			}
		} else {
			return new FungibleFormulaInputOutputVerdict(input, output, Collections.emptyList(), Collections.emptyMap());
		}
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
