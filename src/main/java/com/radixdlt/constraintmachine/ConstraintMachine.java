package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atoms.DataPointer;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import com.radixdlt.utils.UInt256;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import com.radixdlt.atoms.Particle;
import com.radixdlt.store.CMStores;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * An implementation of a UTXO based constraint machine which uses Radix's atom structure.
 */
public final class ConstraintMachine {
	public static class Builder {
		private UnaryOperator<CMStore> virtualStore;
		private ImmutableList.Builder<KernelConstraintProcedure> kernelConstraintProcedureBuilder = new ImmutableList.Builder<>();
		private BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
		private BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;

		public Builder virtualStore(UnaryOperator<CMStore> virtualStore) {
			this.virtualStore = virtualStore;
			return this;
		}

		public Builder addProcedure(KernelConstraintProcedure kernelConstraintProcedure) {
			kernelConstraintProcedureBuilder.add(kernelConstraintProcedure);
			return this;
		}

		public Builder setParticleProcedures(BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures) {
			this.particleProcedures = particleProcedures;
			return this;
		}

		public Builder setWitnessValidators(BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators) {
			this.witnessValidators = witnessValidators;
			return this;
		}


		public ConstraintMachine build() {
			if (virtualStore == null) {
				virtualStore = UnaryOperator.identity();
			}

			return new ConstraintMachine(
				virtualStore,
				kernelConstraintProcedureBuilder.build(),
				particleProcedures,
				witnessValidators
			);
		}
	}

	private final UnaryOperator<CMStore> virtualStore;
	private final ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures;
	private final BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures;
	private final BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators;
	private final CMStore localEngineStore;

	ConstraintMachine(
		UnaryOperator<CMStore> virtualStore,
		ImmutableList<KernelConstraintProcedure> kernelConstraintProcedures,
		BiFunction<Particle, Particle, TransitionProcedure<Particle, Particle>> particleProcedures,
		BiFunction<Particle, Particle, WitnessValidator<Particle, Particle>> witnessValidators
	) {
		Objects.requireNonNull(virtualStore);

		this.virtualStore = Objects.requireNonNull(virtualStore);
		this.localEngineStore = this.virtualStore.apply(CMStores.empty());
		this.kernelConstraintProcedures = kernelConstraintProcedures;
		this.particleProcedures = particleProcedures;
		this.witnessValidators = witnessValidators;
	}

	Optional<CMError> validate(ParticleGroup group, long groupIndex, AtomMetadata metadata) {
		ProcedureResult lastResult = null;
		SpunParticle spunParticleRemaining = null;

		for (int i = 0; i < group.getParticleCount(); i++) {
			final DataPointer dp = DataPointer.ofParticle((int) groupIndex, i);
			final SpunParticle nextSpun = group.getSpunParticle(i);
			final Particle nextParticle = nextSpun.getParticle();
			final Particle curParticle = spunParticleRemaining == null ? null : spunParticleRemaining.getParticle();

			if (spunParticleRemaining != null && spunParticleRemaining.getSpin() == nextSpun.getSpin()) {
				return Optional.of(
					new CMError(
						dp,
						CMErrorCode.PARTICLE_REGISTER_SPIN_CLASH,
						"Current state: " + spunParticleRemaining + " " + lastResult
					)
				);
			}

			final Particle inputParticle = nextSpun.getSpin() == Spin.DOWN ? nextParticle : curParticle;
			final Particle outputParticle = nextSpun.getSpin() == Spin.DOWN ? curParticle : nextParticle;

			final TransitionProcedure<Particle, Particle> transitionProcedure = this.particleProcedures.apply(inputParticle, outputParticle);

			if (transitionProcedure == null) {
				if (inputParticle == null || outputParticle == null) {
					spunParticleRemaining = nextSpun;
					continue;
				}

				return Optional.of(
					new CMError(
						dp,
						CMErrorCode.MISSING_TRANSITION_PROCEDURE,
						"Current state: " + spunParticleRemaining + " " + lastResult
					)
				);
			}

			final Object inputUsed = lastResult != null ? lastResult.getInputUsed() : null;
			final Object outputUsed = lastResult != null ? lastResult.getOutputUsed() : null;

			final ProcedureResult result = transitionProcedure.execute(inputParticle, inputUsed, outputParticle, outputUsed);
			switch (result.getCmAction()) {
				case POP_INPUT:
					if (nextSpun.getSpin() == Spin.UP) {
						spunParticleRemaining = nextSpun;
					}
					break;
				case POP_OUTPUT:
					if (nextSpun.getSpin() == Spin.DOWN) {
						spunParticleRemaining = nextSpun;
					}
					break;
				case POP_INPUT_OUTPUT:
					spunParticleRemaining = null;
					if (result.getUsed() != null) {
						throw new IllegalStateException("POP_INPUT_OUTPUT must output null");
					}
					break;
				case ERROR:
					return Optional.of(
						new CMError(
							dp,
							CMErrorCode.TRANSITION_ERROR,
							result.getErrorMessage()
								+ "Current state: " + spunParticleRemaining + " " + lastResult
						)
					);
			}

			final WitnessValidator<Particle, Particle> witnessValidator = this.witnessValidators.apply(inputParticle, outputParticle);
			if (witnessValidator == null) {
				throw new IllegalStateException("No witness validator for: " + inputParticle + " -> " + outputParticle);
			}
			final Optional<String> witnessErrorMessage = witnessValidator.validate(result.getCmAction(), inputParticle, outputParticle, metadata);
			if (witnessErrorMessage.isPresent()) {
				return Optional.of(
					new CMError(
						dp,
						CMErrorCode.WITNESS_ERROR,
						witnessErrorMessage.get()
					)
				);
			}

			lastResult = result;
		}

		if (spunParticleRemaining != null) {
			return Optional.of(
				new CMError(
					DataPointer.ofParticleGroup((int) groupIndex),
					CMErrorCode.UNEQUAL_INPUT_OUTPUT,
					"Current state: " + spunParticleRemaining + " " + lastResult
				)
			);
		}

		return Optional.empty();
	}

	/**
	 * Validates an atom and calculates the necessary state checks and post-validation
	 * write logic.
	 *
	 * @param cmAtom atom to validate
	 * @param getAllErrors if true, returns all errors, otherwise, fails fast
	 * and just returns the first error
	 * @return results of validation, including any errors, warnings, and post-validation write logic
	 */
	public ImmutableSet<CMError> validate(CMAtom cmAtom, boolean getAllErrors) {
		// "Segfaults" or particles which should not exist
		final Stream<CMError> unknownParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> !localEngineStore.getSpin(p.getParticle()).isPresent())
			.map(p -> new CMError(p.getDataPointer(), CMErrorCode.UNKNOWN_PARTICLE));

		// Virtual particle state checks
		// TODO: Is this better suited at the state check pipeline?
		final Stream<CMError> virtualParticleErrors = cmAtom.getParticles().stream()
			.filter(p -> {
				Particle particle = p.getParticle();
				Spin nextSpin = p.getNextSpin();
				TransitionCheckResult result = SpinStateTransitionValidator.checkParticleTransition(
					particle,
					nextSpin, localEngineStore
				);

				return result.equals(TransitionCheckResult.CONFLICT);
			})
			.map(p ->  new CMError(p.getDataPointer(), CMErrorCode.INTERNAL_SPIN_CONFLICT));

		// "Kernel" checks
		final Stream<CMError> kernelErrs = kernelConstraintProcedures.stream()
			.flatMap(kernelProcedure -> kernelProcedure.validate(cmAtom))
			.map(CMErrors::fromKernelProcedureError);

		// "Application" checks
		final ImmutableAtom atom = cmAtom.getAtom();
		final AtomMetadata metadata = new AtomMetadataFromAtom(atom);
		final Stream<CMError> applicationErrs = Streams.mapWithIndex(atom.particleGroups(), (group, i) ->
			this.validate(group, i, metadata)).flatMap(i -> i.map(Stream::of).orElse(Stream.empty()));

		final Stream<CMError> errorStream = Streams.concat(
			unknownParticleErrors,
			virtualParticleErrors,
			kernelErrs,
			applicationErrs
		);

		return getAllErrors
			? errorStream.collect(ImmutableSet.toImmutableSet())
			: errorStream.findAny().map(ImmutableSet::of).orElse(ImmutableSet.of());
	}

	public UnaryOperator<CMStore> getVirtualStore() {
		return this.virtualStore;
	}
}
