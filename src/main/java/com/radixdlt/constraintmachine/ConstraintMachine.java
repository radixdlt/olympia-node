package com.radixdlt.constraintmachine;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.radixdlt.atoms.ImmutableAtom;
import com.radixdlt.atoms.ParticleGroup;
import com.radixdlt.atoms.Spin;
import com.radixdlt.atoms.SpunParticle;
import com.radixdlt.common.Pair;
import com.radixdlt.constraintmachine.TransitionProcedure.ProcedureResult;
import com.radixdlt.store.CMStore;
import com.radixdlt.store.SpinStateTransitionValidator;
import com.radixdlt.store.SpinStateTransitionValidator.TransitionCheckResult;
import java.util.concurrent.atomic.AtomicReference;
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

	private Stream<ProcedureError> validate(ParticleGroup group, AtomMetadata metadata) {
		final AtomicReference<Pair<SpunParticle, AtomicReference<Object>>> particleRegister = new AtomicReference<>();

		for (int i = 0; i < group.getParticleCount(); i++) {
			final SpunParticle nextSpun = group.getSpunParticle(i);
			final Particle nextParticle = nextSpun.getParticle();
			final AtomicReference<Object> nextData = new AtomicReference<>();
			final SpunParticle curSpun = particleRegister.get() == null ? null : particleRegister.get().getFirst();
			final Particle curParticle = curSpun == null ? null : curSpun.getParticle();
			final AtomicReference<Object> curData = particleRegister.get() == null ? null : particleRegister.get().getSecond();

			if (curSpun != null && curSpun.getSpin() == nextSpun.getSpin()) {
				return Stream.of(ProcedureError.of("Spin Clash: Next particle: " + nextSpun + " Current register: " + particleRegister.get()));
			}

			final Particle inputParticle = nextSpun.getSpin() == Spin.DOWN ? nextParticle : curParticle;
			final AtomicReference<Object> inputData = nextSpun.getSpin() == Spin.DOWN ? nextData : curData;
			final Particle outputParticle = nextSpun.getSpin() == Spin.DOWN ? curParticle : nextParticle;
			final AtomicReference<Object> outputData = nextSpun.getSpin() == Spin.DOWN ? curData : nextData;

			final TransitionProcedure<Particle, Particle> transitionProcedure = this.particleProcedures.apply(inputParticle, outputParticle);

			if (transitionProcedure == null) {
				if (inputParticle == null || outputParticle == null) {
					particleRegister.set(Pair.of(nextSpun, nextData));
					continue;
				}

				return Stream.of(ProcedureError.of("No procedure for Input: " + inputParticle + " Output: " + outputParticle));
			}

			final ProcedureResult result = transitionProcedure.execute(inputParticle, inputData, outputParticle, outputData);
			switch (result.getCmAction()) {
				case POP_INPUT:
					if (nextSpun.getSpin() == Spin.UP) {
						particleRegister.set(Pair.of(nextSpun, nextData));
					}
				case POP_OUTPUT:
					if (nextSpun.getSpin() == Spin.DOWN) {
						particleRegister.set(Pair.of(nextSpun, nextData));
					}
					break;
				case POP_INPUT_OUTPUT:
					particleRegister.set(null);
					break;
				case ERROR:
					return Stream.of(ProcedureError.of("Next particle " + nextParticle + " failed. Current register: " + particleRegister.get()));
			}

			final WitnessValidator<Particle, Particle> witnessValidator = this.witnessValidators.apply(inputParticle, outputParticle);
			final boolean witnessResult = witnessValidator.validate(result.getCmAction(), inputParticle, outputParticle, metadata);
			if (!witnessResult) {
				return Stream.of(ProcedureError.of("Witness failed"));
			}
		}

		if (particleRegister.get() != null) {
			return Stream.of(ProcedureError.of("Particle register not empty: " + particleRegister));
		}

		return Stream.empty();
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
			this.validate(group, metadata).map(issue -> CMErrors.fromProcedureError(issue, (int) i))
		).flatMap(i -> i);

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
